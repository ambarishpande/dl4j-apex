package com.github.ambarishpande.MasterWorkerModule;

import com.datatorrent.api.Context;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.common.util.BaseOperator;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.NullArgumentException;

import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;

/**
 * Created by @ambarishpande on 14/1/17.
 */
public class Dl4jWorkerOperator extends BaseOperator
{

  private long windowId;
  private static final Logger LOG = LoggerFactory.getLogger(Dl4jWorkerOperator.class);

  private MultiLayerConfiguration conf;
  @FieldSerializer.Bind(JavaSerializer.class)
  private MultiLayerNetwork model;
  private boolean hold;
  //  private ArrayList<DataSet> buffer;
  private Queue<DataSet> buffer;
  private int batchSize;
  private int tuplesPerWindow;
  private int numOfTuples = 0;
  private int workerId;
  int count;
  public transient DefaultInputPort<DataSet> dataPort = new DefaultInputPort<DataSet>()
  {

    @Override
    public void process(DataSet data)
    {
      count++;
      double start = System.currentTimeMillis();
      try {

        if (hold) {
          LOG.info("Storing Data in Buffer...");
          buffer.add(data);
        } else {
          while (numOfTuples < batchSize) {

            if (!(buffer.isEmpty())) {
              LOG.info("Fitting over buffered datasets");
              DataSet d = buffer.remove();
              model.fit(d);
              numOfTuples++;

            } else {
              model.fit(data);
              LOG.info("Fitting over normal dataset...");
              numOfTuples++;

            }

          }

          LOG.info("Fitted on " + numOfTuples);
          LOG.info("Sending Model to Parameter Averager...");
          output.emit(model);
          hold = true;
          LOG.info("Holding worker " + workerId);
          LOG.info("New newModel given to ParameterAverager...");
          numOfTuples = 0;
        }

      } catch (NullArgumentException e) {
        LOG.error("Null Pointer exception" + e.getMessage());
      }

      long end = System.currentTimeMillis();
      LOG.info("Time take by worker {} ", (end - start));

    }

  };

  public transient DefaultInputPort<MultiLayerNetwork> controlPort = new DefaultInputPort<MultiLayerNetwork>()
  {
    @Override
    public void process(MultiLayerNetwork newModel)
    {

      LOG.info("Parameters received from Master...");
      model = newModel.clone();
      LOG.info("Resuming Worker " + workerId);
      hold = false;

    }
  };

  public transient DefaultOutputPort<MultiLayerNetwork> output = new DefaultOutputPort<MultiLayerNetwork>();

  public void setup(Context.OperatorContext context)
  {
    LOG.info("Setup Started...");
    model = new MultiLayerNetwork(conf);
    model.init();
    hold = false;
    count = 0;
    tuplesPerWindow = 5;
    buffer = new LinkedList<>();
    workerId = context.getId();
    LOG.info(" Worker ID : " + context.getId());
    LOG.info("Setup Completed...");

  }

  public void beginWindow(long windowId)
  {
    this.windowId = windowId;
  }

  public void endWindow(){
    LOG.info("Worker : " + workerId + " processed " + count +" tuples in window "+ windowId);
    count = 0;
  }
  public void setConf(MultiLayerConfiguration conf)
  {
    this.conf = conf;
  }

  public MultiLayerNetwork getModel()
  {
    return model;
  }

  public int getBatchSize()
  {
    return batchSize;
  }

  public void setBatchSize(int batchSize)
  {
    this.batchSize = batchSize;
  }
}


