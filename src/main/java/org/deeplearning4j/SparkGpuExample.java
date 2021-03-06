package org.deeplearning4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.serializer.KryoRegistrator;
import org.apache.spark.serializer.KryoSerializer;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.conf.override.ClassifierOverride;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * @author sonali
 */
public class SparkGpuExample {

    public static void main(String[] args) throws Exception {
        Nd4j.MAX_ELEMENTS_PER_SLICE = Integer.MAX_VALUE;
        Nd4j.MAX_SLICES_TO_PRINT = Integer.MAX_VALUE;

        // set to test mode
        // this is where you configure Spark
        SparkConf sparkConf = new SparkConf()
                .setMaster("local[*]").set(SparkDl4jMultiLayer.AVERAGE_EACH_ITERATION,"false")
                .set("spark.akka.frameSize", "100")
                .setAppName("mnist");

        System.out.println("Setting up Spark Context...");

        JavaSparkContext sc = new JavaSparkContext(sparkConf);

        //This is where you configure your deep-belief net
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .momentum(0.9).iterations(10)
                .weightInit(WeightInit.DISTRIBUTION).batchSize(10000)
                .dist(new NormalDistribution(0, 1)).lossFunction(LossFunctions.LossFunction.RMSE_XENT)
                .nIn(784).nOut(10).layer(new RBM())
                .list(4).hiddenLayerSizes(600, 500, 400)
                .override(3, new ClassifierOverride()).build();

        //and here you bring Spark and the MultiLayer neural net together...
        System.out.println("Initializing network");
        SparkDl4jMultiLayer master = new SparkDl4jMultiLayer(sc,conf);
        DataSet d = new MnistDataSetIterator(60000,60000).next();
        List<DataSet> next = d.asList();

        //RDDs... the data structure of Spark 1.2
        //Calling fit makes the net learn the data.
        JavaRDD<DataSet> data = sc.parallelize(next);
        MultiLayerNetwork network2 = master.fitDataSet(data);

        Evaluation evaluation = new Evaluation();
        evaluation.eval(d.getLabels(),network2.output(d.getFeatureMatrix()));
        System.out.println("Averaged once " + evaluation.stats());


        INDArray params = network2.params();
        Nd4j.writeTxt(params,"params.txt",",");
        FileUtils.writeStringToFile(new File("conf.json"), network2.getLayerWiseConfigurations().toJson());
    }
}
