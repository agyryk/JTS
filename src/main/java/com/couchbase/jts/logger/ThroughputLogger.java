package com.couchbase.jts.logger;

import com.couchbase.jts.properties.TestProperties;
import com.couchbase.jts.utils.LogPair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by oleksandr.gyryk on 10/2/17.
 */
public class ThroughputLogger extends Logger{

    private long timeStart = 0;
    private int requestsSinceLastDrop = 0;
    private int aggregationBufferMS = 1000;
    private long samplesCounter = 0;
    private String filename;

    public ThroughputLogger(int storageLimit, int loggerId){
        super(storageLimit, loggerId);
        filename = "worker_" + loggerId + "_throughput.log";
    }

    public ThroughputLogger(int storageLimit, int loggerId, String customPrefix){
        super(storageLimit, loggerId);
        filename = customPrefix + "_worker_" + loggerId + "_throughput.log";
    }

    public void logRequest(){
        long logTime = timeStamp();
        if (timeStart == 0) {
            timeStart = timeStamp();
            requestsSinceLastDrop = 1;
            samplesCounter = 1;
        } else if ((logTime - timeStart) < aggregationBufferMS) {
            requestsSinceLastDrop ++;
        } else {
            timeStart = logTime;
            drop(samplesCounter, requestsSinceLastDrop);
            samplesCounter++;
            requestsSinceLastDrop = 1;
        }
    }

    public void dumpThroughput() throws IOException{
        drop(samplesCounter, requestsSinceLastDrop);
        dump(filename);
    }


    public static float aggregate(int totalFilesExpected) throws IOException{
        List<LogPair> lines = new ArrayList<>();
        for (int i=0; i< totalFilesExpected; i++) {
            String filename = "logs/"  + TestProperties.CONSTANT_JTS_LOG_DIR + "/worker_" + i + "_throughput.log";
            Stream<String> strm;
            try {
                strm = Files.lines(Paths.get(filename));
            } catch (IOException ioex) {
                System.err.println("Aggregating throughput: " + ioex.getMessage());
                continue;
            }
            try (Stream<String> stream = strm) {
                stream.forEach(x -> lines.add(new LogPair(x)));
            }
        }

        LogPair[] pairsArr = lines.toArray(new LogPair[lines.size()]);
        Arrays.sort(pairsArr, (a, b) -> a.k.compareTo(b.k));
        dump("combined_throughput.log", pairsArr, pairsArr.length);

        List<LogPair> aggregates = new ArrayList<>();
        long lastSample = -1;
        float totalrequests = 0;
        for (LogPair pair: pairsArr){
            if (lastSample < 0) {
                lastSample = pair.k;
                totalrequests += pair.v;
                continue;
            }
            if (lastSample == pair.k) {
                totalrequests += pair.v;
            } else {
                aggregates.add(new LogPair(lastSample, totalrequests));
                lastSample = pair.k;
                totalrequests = pair.v;
            }
        }

        dump("aggregated_throughput.log", aggregates.toArray(new LogPair[aggregates.size()]),
                aggregates.size());

        int totalValues = aggregates.size();
        float sum = 0;
        for (LogPair pair: aggregates){
            sum += pair.v;
        }

        if (totalValues != 0) {
            return sum/totalValues;
        }

        return 0;
    }
}
