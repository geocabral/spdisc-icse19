spdisc-icse19
ORB (Oversampling Rate Boosting) - An Online Method for Updating the 			   
  Oversampling Rate of the Classes Based on the Moving Average of 
	            the Classifier's Predictions 		       		              
====================================================================
This project consists of a Java implementation of the online classification method ORB (Oversampling Rate Boosting) introduced in "Class Imbalance Evolution and Verification Latency in Just-in-Time Software Defect Prediction" (ICSE'19). As stated in the paper title, the method was specifically designed for Just-in-time Software Defect Prediction (JIT-SDP), i.e., software defect prediction in the commit time. In addition, ORB is built upon the OOB[1] which in turn is built upon the Massive Online Analysis (MOA) framework [2]. So far, the ORB method was only tested in software repository data from open source projects available on Github.

— — — Installation

The project was built using the JDK and JRE 1.8. In order to use the method, one must install the JDK 1.8 and edit the file spdisc.tests.Experiment.java. We strongly recommend using a Java IDE such as Eclipse.

— — — Basic Usage

The usage of the method consists in building a command in the same format as a MOA command. This command is located in the spdisc.tests.Experiment.java file.

For the following command, for example:

EvaluatePrequentialWFL -l (spdisc.meta.WFL_OO_ORB_Oza -i 15 -s 20 -t 0.99 -w 90 -p 100;0.4;10;12;1.5;3) -s (ArffFileStream -f (datasets/neutron.arff) -c 15) -e (FadingFactorEachClassPerformanceEvaluator -a 0.99) -f 1 -d /results/neutron.csv

EvaluatePrequentialWFL - The evaluation method. In this case, the evaluator considers the verification latency (i.e. the delay for obtaining the true commit labels)

spdisc.meta.WFL_OO_ORB_Oza - The class that implements the ORB

-i 15 - the position of the unixtimestamp of the commit in the arff

-s 20 - the ensemble size

-t 0.99 - the fading factor[3] used for computing the class sizes

-w 90 - the waiting time for assuming the commit label is available

-p 100;0.4;10;12;1.5;3 - the parameters for the ORB.

100 - the size of the time window for computing the moving average of the predictions

0.4 - the threshold for the moving average of the predictions. It decides wether emphasize the clean or buggy class (emphasize clean class if the threshold is over 0.4, emphasize the defect-inducing class otherwise)

10 - controls the maximum value for overemphasizing the clean class (i.e., the maximum value becomes 10+1)

12 - controls the maximum value for overemphasizing the defect-inducing class (i.e., the maximum value becomes 12+1)

1.5 - controls the curve of the function used to compute the boosting factor. As this value increases, the function becomes steeper when the current prediction moving average gets farther from the the threshold for the moving average of the predictions (i.e., 0.4 in this case).

3 - detects noisy defect-inducing commits. If a defect-inducing commit is equal to at least 3 past clean commit, it is considered as noise.

-f (datasets/neutron.arff) -c 15 - the location of the arff file and the position of the label attribute.

-e (FadingFactorEachClassPerformanceEvaluator -a 0.99) - the performance evaluator method with the fading factor of 0.99.

-d /results/neutron.csv - the location of the result file.

— — — Mounting the ARFF file

The arff file must be built using the following header format:

@relation 'relation_name'

@attribute fix {False,True}

@attribute ns numeric

@attribute nd numeric

@attribute nf numeric

@attribute entrophy numeric

@attribute la numeric

@attribute ld numeric

@attribute lt numeric

@attribute ndev numeric

@attribute age numeric

@attribute nuc numeric

@attribute exp numeric

@attribute rexp numeric

@attribute sexp numeric

@attribute contains_bug {False,True}

@attribute author_date_unix_timestamp numeric

@attribute commit_type numeric

@data

…

The attribute author_date_unix_timestamp contains the time stamp of the commit.
The attribute commit_type indicates the situation of the commit. 0 - the commit is genuinely clean, 1 - the commit is defect-inducing but its true label is not available before w days, 2 - the commit is defect-inducing and its true label is available before w days, and 3 - the commit is a copy of a past defect-inducing commit used to inform when the commit was found to be defect inducing.
The meaning of the remaining attributes can be found in [4]
— — — Licensing

Please see LICENSE for more info.

— — — References

[1] S. Wang, L. L. Minku and X. Yao, "Resampling-Based Ensemble Methods for Online Class Imbalance Learning," in IEEE Transactions on Knowledge and Data Engineering, vol. 27, no. 5, pp. 1356-1368, 1 May 2015.

[2] Albert Bifet, Geoff Holmes, Richard Kirkby, Bernhard Pfahringer (2010); MOA: Massive Online Analysis; Journal of Machine Learning Research 11: 1601-1604.

[3] J. Gama, R. Sebastião, and P. P. Rodrigues, “On evaluating stream learning algorithms”, Machine Learning, vol. 90, no. 3, pp. 317–346,2013.

[4] Y. Kamei, E. Shihab, B. Adams, A. E. Hassan, A. Mockus, A. Sinha,and N. Ubayashi, “A large-scale empirical study of just-in-time qualityassurance,”IEEE Transactions on Software Engineering (TSE), vol. 39,no. 6, pp. 757–773, 2013.