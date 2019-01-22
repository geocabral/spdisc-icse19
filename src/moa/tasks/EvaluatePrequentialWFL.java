/*
 *    EvaluatePrequentialWFL.java
 *    Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 *    @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *    
 *    This version of the evaluation prequential considers the verification latency for 
 *    Jst-in-time Software Defect Prediction by means of the Wait-for-Labels framework, proposed 
 *    in Minku et. al. ICSE'19 (paper name).  
 *    
 */
package moa.tasks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.MultiClassClassifier;
import moa.core.Example;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.core.TimingUtils;
import moa.core.Utils;
import moa.evaluation.EWMAClassificationPerformanceEvaluator;
import moa.evaluation.FadingFactorClassificationPerformanceEvaluator;
import moa.evaluation.LearningCurve;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.WindowClassificationPerformanceEvaluator;
import moa.learners.Learner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;

/**
 * Task for evaluating a classifier on a stream by testing then training with
 * each example in sequence.
 *
 * @author George Cabral (george.gcabral@ufrpe.br)
 * @author Leandro Minku (L.L.Minku@cs.bham.ac.uk)
 * @version $Revision: 0.1 $
 */
public class EvaluatePrequentialWFL extends ClassificationMainTask {

	public int bugsForAddTrainingORB = 0;

	@Override
	public String getPurposeString() {
		return "Evaluates a classifier on a stream by testing then training with each example in sequence and respecting the "
				+ "waiting period for finding the true labels.";
	}

	// Code representing each commit label scenario.
	// the commit is not buggy
	private static final int NOT_BUG = 0;
	// the commit is buggy but its true label was not found within W days
	private static final int BUG_NOT_DISCOVERED_W_DAYS = 1;
	// the commit is buggy and its true label was found within W days
	private static final int BUG_DISCOVERED_W_DAYS = 2;
	// the true label of a defective commit was assigned.
	private static final int BUG_FOUND = 3;

	private static final long serialVersionUID = 1L;

	public ClassOption learnerOption = new ClassOption("learner", 'l', "Learner to train.", MultiClassClassifier.class,
			"moa.classifiers.bayes.NaiveBayes");

	public ClassOption streamOption = new ClassOption("stream", 's', "Stream to learn from.", ExampleStream.class,
			"generators.RandomTreeGenerator");

	public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
			"Classification performance evaluation method.", LearningPerformanceEvaluator.class,
			"WindowClassificationPerformanceEvaluator");

	public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
			"Maximum number of instances to test/train on  (-1 = no limit).", 100000000, -1, Integer.MAX_VALUE);

	public IntOption timeLimitOption = new IntOption("timeLimit", 't',
			"Maximum number of seconds to test/train for (-1 = no limit).", -1, -1, Integer.MAX_VALUE);

	public IntOption sampleFrequencyOption = new IntOption("sampleFrequency", 'f',
			"How many instances between samples of the learning performance.", 100000, 0, Integer.MAX_VALUE);

	public IntOption memCheckFrequencyOption = new IntOption("memCheckFrequency", 'q',
			"How many instances between memory bound checks.", 100000, 0, Integer.MAX_VALUE);

	public FileOption dumpFileOption = new FileOption("dumpFile", 'd', "File to append intermediate csv results to.",
			null, "csv", true);

	public FileOption outputPredictionFileOption = new FileOption("outputPredictionFile", 'o',
			"File to append output predictions to.", null, "pred", true);

	// New for prequential method DEPRECATED
	public IntOption widthOption = new IntOption("width", 'w', "Size of Window", 1000);

	public FloatOption alphaOption = new FloatOption("alpha", 'a', "Fading factor or exponential smoothing factor",
			.01);
	// End New for prequential methods

	@Override
	public Class<?> getTaskResultType() {
		return LearningCurve.class;
	}

	@Override
	protected Object doMainTask(TaskMonitor monitor, ObjectRepository repository) {

		Learner learner = (Learner) getPreparedClassOption(this.learnerOption);
		ExampleStream stream = (ExampleStream) getPreparedClassOption(this.streamOption);
		LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(
				this.evaluatorOption);
		LearningCurve learningCurve = new LearningCurve("learning evaluation instances");

		// New for prequential methods
		if (evaluator instanceof WindowClassificationPerformanceEvaluator) {
			// ((WindowClassificationPerformanceEvaluator)
			// evaluator).setWindowWidth(widthOption.getValue());
			if (widthOption.getValue() != 1000) {
				System.out
						.println("DEPRECATED! Use EvaluatePrequential -e (WindowClassificationPerformanceEvaluator -w "
								+ widthOption.getValue() + ")");
				return learningCurve;
			}
		}
		if (evaluator instanceof EWMAClassificationPerformanceEvaluator) {
			// ((EWMAClassificationPerformanceEvaluator)
			// evaluator).setalpha(alphaOption.getValue());
			if (alphaOption.getValue() != .01) {
				System.out.println("DEPRECATED! Use EvaluatePrequential -e (EWMAClassificationPerformanceEvaluator -a "
						+ alphaOption.getValue() + ")");
				return learningCurve;
			}
		}
		if (evaluator instanceof FadingFactorClassificationPerformanceEvaluator) {
			// ((FadingFactorClassificationPerformanceEvaluator)
			// evaluator).setalpha(alphaOption.getValue());
			if (alphaOption.getValue() != .01) {
				System.out.println(
						"DEPRECATED! Use EvaluatePrequential -e (FadingFactorClassificationPerformanceEvaluator -a "
								+ alphaOption.getValue() + ")");
				return learningCurve;
			}
		}
		// End New for prequential methods

		learner.setModelContext(stream.getHeader());
		int maxInstances = this.instanceLimitOption.getValue();
		long instancesProcessed = 0;
		int maxSeconds = this.timeLimitOption.getValue();
		int secondsElapsed = 0;
		monitor.setCurrentActivity("Evaluating learner...", -1.0);

		File dumpFile = this.dumpFileOption.getFile();
		PrintStream immediateResultStream = null;
		if (dumpFile != null) {
			try {
				if (dumpFile.exists()) {
					immediateResultStream = new PrintStream(new FileOutputStream(dumpFile, true), true);
				} else {
					immediateResultStream = new PrintStream(new FileOutputStream(dumpFile), true);
				}
			} catch (Exception ex) {
				throw new RuntimeException("Unable to open immediate result file: " + dumpFile, ex);
			}
		}
		// File for output predictions
		File outputPredictionFile = this.outputPredictionFileOption.getFile();
		PrintStream outputPredictionResultStream = null;
		if (outputPredictionFile != null) {
			try {
				if (outputPredictionFile.exists()) {
					outputPredictionResultStream = new PrintStream(new FileOutputStream(outputPredictionFile, true),
							true);
				} else {
					outputPredictionResultStream = new PrintStream(new FileOutputStream(outputPredictionFile), true);
				}
			} catch (Exception ex) {
				throw new RuntimeException("Unable to open prediction result file: " + outputPredictionFile, ex);
			}
		}
		boolean firstDump = true;
		boolean preciseCPUTiming = TimingUtils.enablePreciseTiming();
		long evaluateStartTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
		long lastEvaluateStartTime = evaluateStartTime;
		double RAMHours = 0.0;

		while (stream.hasMoreInstances() && ((maxInstances < 0) || (instancesProcessed < maxInstances))
				&& ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {

			boolean write_results = true;

			Example trainInst = stream.nextInstance();
			Example testInst = (Example) trainInst.copy();

			Attribute a = new Attribute("commit_type");
			int commit_type = (int) ((Instance) trainInst.getData()).value(a);

			((Instance) trainInst.getData()).deleteAttributeAt(16);
			((Instance) testInst.getData()).deleteAttributeAt(16);

			if (commit_type == NOT_BUG) {

				double[] prediction = learner.getVotesForInstance(testInst);

				// Output prediction
				if (outputPredictionFile != null) {
					int trueClass = (int) ((Instance) trainInst.getData()).classValue();

					outputPredictionResultStream.println(Utils.maxIndex(prediction) + ","
							+ (((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
				}

				// evaluator.addClassificationAttempt(trueClass, prediction,
				// testInst.weight());
				evaluator.addResult(testInst, prediction);

				learner.trainOnInstance(trainInst);
			}

			if (commit_type == BUG_NOT_DISCOVERED_W_DAYS) {

				((Instance) testInst.getData()).setClassValue(1);
				double[] prediction = learner.getVotesForInstance(testInst);

				// Output prediction
				if (outputPredictionFile != null) {
					int trueClass = (int) ((Instance) testInst.getData()).classValue();

					outputPredictionResultStream.println(Utils.maxIndex(prediction) + ","
							+ (((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
				}

				// evaluator.addClassificationAttempt(trueClass, prediction,
				// testInst.weight());
				evaluator.addResult(testInst, prediction);

				((Instance) trainInst.getData()).setClassValue(0);
				learner.trainOnInstance(trainInst);
			}

			if (commit_type == BUG_DISCOVERED_W_DAYS) {

				((Instance) testInst.getData()).setClassValue(1);
				double[] prediction = learner.getVotesForInstance(testInst);

				// Output prediction
				if (outputPredictionFile != null) {
					int trueClass = (int) ((Instance) trainInst.getData()).classValue();

					outputPredictionResultStream.println(Utils.maxIndex(prediction) + ","
							+ (((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
				}

				// evaluator.addClassificationAttempt(trueClass, prediction,
				// testInst.weight());
				evaluator.addResult(testInst, prediction);

				bugsForAddTrainingORB++;

				// ((Instance) testInst.getData()).setClassValue(0);
				// learner.trainOnInstance(trainInst);
			}

			if (commit_type == BUG_FOUND) {

				// ((Instance) testInst.getData()).setClassValue(1);
				// double[] prediction = learner.getVotesForInstance(testInst);
				//
				// // Output prediction
				// if (outputPredictionFile != null) {
				// int trueClass = (int) ((Instance)
				// trainInst.getData()).classValue();
				//
				// outputPredictionResultStream.println(Utils.maxIndex(prediction)
				// + ","
				// + (((Instance) testInst.getData()).classIsMissing() == true ?
				// " ? " : trueClass));
				// }
				//
				// // evaluator.addClassificationAttempt(trueClass, prediction,
				// // testInst.weight());
				// evaluator.addResult(testInst, prediction);

				((Instance) trainInst.getData()).setClassValue(1);
				learner.trainOnInstance(trainInst);

				if (learner instanceof moa.classifiers.spdisc.meta.WFL_OO_ORB_Oza && bugsForAddTrainingORB > 0) {
					((moa.classifiers.spdisc.meta.WFL_OO_ORB_Oza) learner).idxTr++;
					bugsForAddTrainingORB--;
				}

				write_results = false;
			}

			if (write_results) {
				instancesProcessed++;
				if (instancesProcessed % this.sampleFrequencyOption.getValue() == 0
						|| stream.hasMoreInstances() == false) {
					long evaluateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
					double time = TimingUtils.nanoTimeToSeconds(evaluateTime - evaluateStartTime);
					double timeIncrement = TimingUtils.nanoTimeToSeconds(evaluateTime - lastEvaluateStartTime);
					double RAMHoursIncrement = learner.measureByteSize() / (1024.0 * 1024.0 * 1024.0); // GBs
					RAMHoursIncrement *= (timeIncrement / 3600.0); // Hours
					RAMHours += RAMHoursIncrement;
					lastEvaluateStartTime = evaluateTime;
					learningCurve.insertEntry(new LearningEvaluation(
							new Measurement[] { new Measurement("learning evaluation instances", instancesProcessed)
							// ,
							// new Measurement(
							// "evaluation time ("
							// + (preciseCPUTiming ? "cpu "
							// : "") + "seconds)",
							// time),
							// new Measurement(
							// "model cost (RAM-Hours)",
							// RAMHours)
							}, evaluator, learner));

					if (immediateResultStream != null) {
						if (firstDump) {
							immediateResultStream.println(learningCurve.headerToString());
							firstDump = false;
						}
						immediateResultStream.println(learningCurve.entryToString(learningCurve.numEntries() - 1));
						immediateResultStream.flush();
					}
				}
			}

			if (instancesProcessed % INSTANCES_BETWEEN_MONITOR_UPDATES == 0) {
				if (monitor.taskShouldAbort()) {
					return null;
				}
				long estimatedRemainingInstances = stream.estimatedRemainingInstances();
				if (maxInstances > 0) {
					long maxRemaining = maxInstances - instancesProcessed;
					if ((estimatedRemainingInstances < 0) || (maxRemaining < estimatedRemainingInstances)) {
						estimatedRemainingInstances = maxRemaining;
					}
				}
				monitor.setCurrentActivityFractionComplete(estimatedRemainingInstances < 0 ? -1.0
						: (double) instancesProcessed / (double) (instancesProcessed + estimatedRemainingInstances));
				if (monitor.resultPreviewRequested()) {
					monitor.setLatestResultPreview(learningCurve.copy());
				}
				secondsElapsed = (int) TimingUtils
						.nanoTimeToSeconds(TimingUtils.getNanoCPUTimeOfCurrentThread() - evaluateStartTime);
			}
		}
		if (immediateResultStream != null) {
			immediateResultStream.close();
		}
		if (outputPredictionResultStream != null) {
			outputPredictionResultStream.close();
		}

		return learningCurve;
	}
}
