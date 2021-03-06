import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import qp.operators.Debug;
import qp.operators.JoinType;
import qp.operators.Operator;
import qp.optimizer.BufferManager;
import qp.optimizer.DPoptimizer;
import qp.optimizer.RandomOptimizer;
import qp.utils.*;

import java.io.*;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

/**
 * This class runs the experiment specified in the project description
 */
public class Experiment {

    @Rule
    public TemporaryFolder saveFolder = new TemporaryFolder();
    private String folderRoot;
    private String[] queryFiles = {"query1_1", "query1_2", "query1_3", "query2_1"};
    private int[] buffSizes = {10, 50, 100};
    private ArrayList<SQLQuery> sqlQueries = new ArrayList<>();
    private PrintWriter out;
    private int numAtts;
    private int fileNo;

    private enum testType {
        Random, DP
    }

    private testType toTest = testType.DP;

    @Before
    public void setUp() {
        for (String file : queryFiles) {
            sqlQueries.add(QueryMain.getSqlQuery(file));
        }
        Batch.setPageSize(1000); // bytes per page
        folderRoot = saveFolder.getRoot().getPath();
    }

    @Test
    public void Experiment1() throws Exception {
        for (int i = 0; i < sqlQueries.size() - 1; i++) {
            Debug.printBold("Experiment 1-" + (i + 1));
            SQLQuery query = sqlQueries.get(i);
            if (query.getNumJoin() > 0) {
                BufferManager bf = new BufferManager(4, query.getNumJoin());
            }

            // do block nested first
            JoinType.setNumJoinTypes(2);
            System.out.printf("It took %.4f for Experiment 1-%d with BNJ\n", computeQueryPerformance(query), (i + 1));

            // do sort merge next
            JoinType.setNumJoinTypes(3);
            System.out.printf("It took %.4f for Experiment 1-%d with SMJ\n", computeQueryPerformance(query), (i + 1));
        }

        assertTrue("Experiment 1 is complete", true);
    }

    @Test
    public void TestNJ() throws Exception {
        SQLQuery query = sqlQueries.get(0);
        for (int bsize : buffSizes) {
            Debug.printBold("Experiment with buffsize = " + bsize);
            if (query.getNumJoin() > 0) {
                BufferManager bf = new BufferManager(bsize, query.getNumJoin());
            }
            JoinType.setNumJoinTypes(2);
            System.out.printf("It took %.4f\n", computeQueryPerformance(query));
        }
    }

    @Test
    public void Experiment2() throws Exception {
        Debug.printBold("Experiment 2");
        SQLQuery query = sqlQueries.get(sqlQueries.size() - 1); // set experiment2 query to be the last one
        BufferManager bf = new BufferManager(100, query.getNumJoin());

        // do block nested first
        JoinType.setNumJoinTypes(2);
        System.out.printf("It took %.4f for Experiment 2 with BNJ\n", computeQueryPerformance(query));

        JoinType.setNumJoinTypes(1);
        System.out.printf("It took %.4f for Experiment 2 with PNJ\n", computeQueryPerformance(query));

        // do sort merge next
        JoinType.setNumJoinTypes(3);
        System.out.printf("It took %.4f for Experiment 2 with SMJ\n", computeQueryPerformance(query));

        assertTrue("Experiment 2 is complete", true);
    }

    private double computeQueryPerformance(SQLQuery query) throws IOException {
        String saveLocation = folderRoot + ("FILE" + fileNo);
        fileNo++;
        Operator root;
        if (toTest == testType.Random)
            root = runRandomOptimizer(query);
        else
            root = runDPOptimizer(query);

        Debug.PPrint(root);
        System.out.println();
        out = new PrintWriter(new BufferedWriter(new FileWriter(saveLocation)));
        long starttime = System.currentTimeMillis();
        root.open(); // this MUST be called
        writeResultToFile(root);
        out.close();
        long endtime = System.currentTimeMillis();
        return (endtime - starttime) / (double) 1000;
    }

    private Operator runDPOptimizer(SQLQuery query) {
        DPoptimizer dp = new DPoptimizer(query);
        return DPoptimizer.makeExecPlan(dp.getBestPlan());
    }

    private Operator runRandomOptimizer(SQLQuery query) {
        RandomOptimizer ro = new RandomOptimizer(query);
        Operator logicalroot = ro.getOptimizedPlan();
        assertTrue("logical root is not null", logicalroot != null);
        return RandomOptimizer.makeExecPlan(logicalroot);
    }

    public void writeResultToFile(Operator root) {
        Debug.printWithLines(true, "WriteResultToFile");
        Schema schema = root.getSchema();
        numAtts = schema.getNumCols();
        printSchema(schema);
        Batch resultbatch;
        int tupleCount = 0;

        /* print each tuple in the result */
        while ((resultbatch = root.next()) != null) {
            for (int i = 0; i < resultbatch.size(); i++) {
                printTuple(resultbatch.elementAt(i));
                tupleCount++;

            }
        }
        Debug.printBold("#tuples = " + tupleCount);
        root.close();
    }


    protected void printSchema(Schema schema) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numAtts; i++) {
            Attribute attr = schema.getAttribute(i);
            sb.append(attr.getTabName());
            sb.append(".");
            sb.append(attr.getColName());
            sb.append("  ");
        }
        sb.append("\n");
        out.print(sb.toString());
    }

    /**
     * outputs a tuple in the result query into file
     *
     * @param t tuple
     */
    protected void printTuple(Tuple t) {
        for (int i = 0; i < numAtts; i++) {
            Object data = t.dataAt(i);
            if (data instanceof Integer) {
                out.print(data + "\t");
            } else if (data instanceof Float) {
                out.print(data + "\t");
            } else {
                out.print(data + "\t");
            }
        }
        out.println();
    }
}
