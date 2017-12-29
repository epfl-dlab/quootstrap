package ch.epfl.dlab.spinn3r.converter;

/**
 * This class is used for measuring time.
 */
public class Stopwatch {

	private final long startTime;
	
	public Stopwatch() {
		startTime = System.currentTimeMillis();
	}
	
	public double printTime() {
		long endTime = System.currentTimeMillis();
		double elapsed = (endTime - startTime) / 1000.0;
		System.out.println("Elapsed time: " + elapsed + " seconds.");
		return elapsed;
	}
}
