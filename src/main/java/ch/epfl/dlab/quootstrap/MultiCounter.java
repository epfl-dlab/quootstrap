package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.util.LongAccumulator;

public class MultiCounter implements Serializable {

	private static final long serialVersionUID = -5606791394577221239L;
	
	private final Map<String, LongAccumulator> accumulators;
	
	public MultiCounter(JavaSparkContext sc, String... counters) {
		accumulators = new HashMap<>();
		for (String counter : counters) {
			accumulators.put(counter, sc.sc().longAccumulator());
		}
	}
	
	public void increment(String accumulator) {
		accumulators.get(accumulator).add(1);
	}
	
	public long getValue(String accumulator) {
		return accumulators.get(accumulator).value();
	}
	
	public void dump() {
		accumulators.forEach((k, v) -> System.out.println(k + ": " + v.value()));
	}
}
