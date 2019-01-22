
/**
 * Author: George G. Cabral (george.gcabral@ufrpe.br)
 * Implementation of an Oversampling Online methodology able adapt the oversampling rate 
 * based on the performance of the model in a certain time in the past. Thus, increasing the oversampling
 * rate of the class with poorer performance.
 * 
 * Class Imbalance Evolution and Verification Latency in Just-in-Time Software Defect Prediction, ICSE'19. 
 * 
 * This implements the algorithm proposed in the above paper. So, it should reflect those results.
 * 
 */

package moa.classifiers.spdisc.meta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;

import com.github.javacliparser.FloatOption;
import com.github.javacliparser.StringOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.Classifier;
import moa.classifiers.meta.OzaBag;
import moa.core.DoubleVector;
import moa.core.Measurement;
import moa.core.MiscUtils;

public class OO_ORB_Oza extends OzaBag{

	//the index of the time stamp. Assigned in the child class (WFL_OO_ORB)
	public int idxTimestamp = -1;
	
	//pool containing anticipated defective instances that happened before 90 days and 
	//made the problem imbalanced towards the defective class. 
	//The idea is to release these examples slowly to make the problem less imbalanced towards
	//the defective class in the begining of the  string
	public ArrayList<Instance> poolInitialDefectiveInstances = new ArrayList<>();

	int kdef = 0;
	int kndef =0;
	
	// irrespective to the class, indicates if a defect was detected (TP or FP)
	// used only to store information about the current prediction in the CSV
	// output file
	public double[] vote;
	
	// this array stores the non defective instances that are used to be
	// compared to each defective instance during the training
	// time in order to check whether the defective instance is noisy or not.
	public ArrayList<Instance> pastNonDefectiveInstances = new ArrayList<>();

	// array for early stoping the obf adjustment
	public ArrayList<Integer> pastPredictions = new ArrayList<>();

	// last n models that comprise the number of days to the backtrack model
	public Vector<Classifier[]> poolModels = new Vector<>();

	// last n instances that comprise the instances seen at the moment of each
	// model in the poolModels
	public HashMap<Integer, Vector<Instance>> poolLastInstances = new HashMap<>();

	public double[] votes = { 0.0, 0.0 };

	public double recallVal0 = 0;
	public double recallVal1 = 0;

	public int ctInstances = 0;

	// time stamp of the first day of the stream
	protected long firstTimeStamp = -100;

	public long currentTimeStamp = 0l;

	public Vector<Instance> validationPool = new Vector<>();

	public int idxTr = 0;

	private static final long serialVersionUID = 1L;

	public FloatOption theta = new FloatOption("theta", 't', "The time decay factor for class size.", 0.9, 0, 1);

//	// the days in the past to which the model will be recovered to tackle the
//	// class bias problem
//	public IntOption storedDaysRetraining = new IntOption("storedDaysRetraining", 'r',
//			"The number of days stored in a pool of instances for retraining to recovery from overfitting the minority class.",
//			5, 1, Integer.MAX_VALUE);

	public StringOption classifierOption = new StringOption("classifierOption", 'c',
			"Specific options for the used classifier.", "-m OzaBag -s 20 -l trees.HoeffdingTree");

	protected double classSize[]; // time-decayed size of each class

	@Override
	public String getPurposeString() {
		return "Oversampling on-line bagging of Wang et al IJCAI 2016.";
	}

	public OO_ORB_Oza() {
		super();
		classSize = null;
	}

	@Override
	public void trainOnInstanceImpl(Instance inst) {

		if(inst.classValue() == 1){
			poolInitialDefectiveInstances.add(inst.copy());
		}
		
		if(inst.classValue() == 0){
			trainModel(inst);
			if(!poolInitialDefectiveInstances.isEmpty()){
				trainModel(poolInitialDefectiveInstances.get(0));
				poolInitialDefectiveInstances.remove(0);
			}
		}

	}
	
	
	
	public void trainModel(Instance inst) {
		
		updateClassSize(inst);
		
		double obf = getOBFPredAvg();

		inst.deleteAttributeAt(idxTimestamp); // remove the time
															// stamp before
															// using the
		boolean print = false;
		
		for (int i = 0; i < this.ensemble.length; i++) {

			double lambda = calculatePoissonLambda(inst);
			int k = MiscUtils.poisson(lambda, this.classifierRandom);

			if (inst.classValue() == 1 && obf > 0) {
				k *= obf;				
			}

			if (inst.classValue() == 0 && obf < -1) {

				k *= -obf;
				
			}
			
			if(inst.classValue() ==0){
				kndef+=k;
			}else{
				kdef+=k;
			}
			
			if(print){
				System.out.println("obf: "+ obf+" k:"+ k+" class: "+inst.classValue()+" idxTr: "+idxTr+" k ("+kndef+","+kdef+") ");
				print = false;
			}

			if (k > 0) {
				Instance weightedInst = (Instance) inst.copy();
				weightedInst.setWeight(inst.weight() * k);
				this.ensemble[i].trainOnInstance(weightedInst);

			}
		}

		if (inst.classValue() == 0) {
			idxTr++;
		}

	}
	
	

	

	
	public double getPredAvg() {
		Double average = pastPredictions.stream().mapToInt(val -> val).average().orElse(0.0);
		
		return average;
	}


	
	
	public double getOBFPredAvg() {

		double obf = 1;

		Double average = pastPredictions.stream().mapToInt(val -> val).average().orElse(0.0);

		double threshold = 0.4;
		
		if (average < threshold) {
			
			double y = 1.5;
			
			average = Math.abs(average - threshold);
			
			obf = ((Math.pow(y,(average*10))-1)/(Math.pow(y,threshold*10) - 1)*12.)+1;
			
			
		} else {

			double y = 1.5;
			
			obf = ((Math.pow(y,(average*10))-Math.pow(y,(threshold*10)))/(Math.pow(y,(10)) - Math.pow(y,(threshold * 10)))*10)+1;
			
			
			obf = obf * -1;
		}

		if(Math.abs(obf) < 1){
			obf = 1;
		}
		
		return obf;
	}

	@Override
	public double[] getVotesForInstance(Instance inst) {

		double[] combinedVote = super.getVotesForInstance(inst);

		if (combinedVote.length == 2) {
			if (combinedVote[0] > combinedVote[1]) {
				pastPredictions.add(0);
			} else {
				pastPredictions.add(1);
			}

			if (pastPredictions.size() > 100) {
				pastPredictions.remove(0);
			}
		}

		ctInstances++;

		vote = new double[2];

		if (combinedVote.length == 2 && combinedVote[1] > combinedVote[0]) {
			vote[0] = 0;
			vote[1] = 1;
		} else {
			vote[0] = 1;
			vote[1] = 0;
		}

		return combinedVote;
	}



	public double[] getVotes(Instance inst) {
		DoubleVector combinedVote = new DoubleVector();

		Classifier[] arrCls = this.ensemble;

		for (int i = 0; i < arrCls.length; i++) {

			DoubleVector vote = new DoubleVector(arrCls[i].getVotesForInstance(inst));
			if (vote.sumOfValues() > 0.0) {
				vote.normalize();
				combinedVote.addValues(vote);
			}
		}
		return combinedVote.getArrayRef();
	}

	protected void updateClassSize(Instance inst) {
		if (this.classSize == null) {
			classSize = new double[inst.numClasses()];

			for (int i = 0; i < classSize.length; ++i) {
				classSize[i] = 1d / classSize.length;
			}
		}

		for (int i = 0; i < classSize.length; ++i) {
			classSize[i] = theta.getValue() * classSize[i]
					+ (1d - theta.getValue()) * ((int) inst.classValue() == i ? 1d : 0d);
		}
	}

	// classInstance is the class corresponding to the instance for which we
	// want to calculate lambda
	// will result in an error if classSize is not initialised yet
	// OVERSAMPLING
	public double calculatePoissonLambda(Instance inst) {
		double lambda = 1d;
		int majClass = getMajorityClass();

		lambda = classSize[majClass] / classSize[(int) inst.classValue()];

		return lambda;
	}

	// will result in an error if classSize is not initialised yet
	public int getMajorityClass() {
		int indexMaj = 0;

		for (int i = 1; i < classSize.length; ++i) {
			if (classSize[i] > classSize[indexMaj]) {
				indexMaj = i;
			}
		}
		return indexMaj;
	}

	// will result in an error if classSize is not initialised yet
	public int getMinorityClass() {
		int indexMin = 0;

		for (int i = 1; i < classSize.length; ++i) {
			if (classSize[i] <= classSize[indexMin]) {
				indexMin = i;
			}
		}
		return indexMin;
	}

	// will result in an error if classSize is not initialised yet
	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		Measurement[] measure = super.getModelMeasurementsImpl();
		Measurement[] measurePlus = null;

		if (classSize != null) {
			measurePlus = new Measurement[measure.length + classSize.length + 2];

			for (int i = 0; i < measure.length; ++i) {
				measurePlus[i] = measure[i];
			}

			for (int i = 0; i < classSize.length; ++i) {
				String str = "size of class " + i;
				measurePlus[measure.length + i] = new Measurement(str, classSize[i]);
			}

			for (int i = 0; i < classSize.length; ++i) {
				String str = "vote for class " + i;
				measurePlus[measure.length + 2 + i] = new Measurement(str, vote[i]);
			}

		} else {
			measurePlus = new Measurement[measure.length + 4];
			for (int i = 0; i < measure.length; ++i) {
				measurePlus[i] = measure[i];
			}

			for (int i = 0; i < 2; ++i) {
				String str = "size of class " + i;
				measurePlus[measure.length + i] = new Measurement(str, 0);
			}

			for (int i = 0; i < 2; ++i) {
				String str = "vote for class " + i;
				measurePlus[measure.length + 2 + i] = new Measurement(str, votes[i]);
			}
		}
		// measurePlus = measure;

		return measurePlus;
	}

	@Override
	public boolean isRandomizable() {
		return true;
	}

}

class OptionsClassifier {

	public int getIntOption(char identifier, String opts) {

		int ret = 0;
		StringTokenizer strTok = new StringTokenizer(opts);

		while (strTok.hasMoreTokens()) {
			String s = strTok.nextToken();

			if (s.equals("-" + identifier)) {
				ret = new Integer(strTok.nextToken());
				break;
			}
		}

		return ret;
	}

	public String getStringOption(char identifier, String opts) {

		String ret = "";
		StringTokenizer strTok = new StringTokenizer(opts);

		while (strTok.hasMoreTokens()) {
			String s = strTok.nextToken();

			if (s.equals("-" + identifier)) {
				ret = strTok.nextToken();
				break;
			}
		}

		return ret;
	}

}
