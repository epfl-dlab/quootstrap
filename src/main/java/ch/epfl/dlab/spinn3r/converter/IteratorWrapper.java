package ch.epfl.dlab.spinn3r.converter;
import java.util.Iterator;

/**
 * Wraps an Iterator<T> inside an Iterable<T>.
 */
public class IteratorWrapper<T> implements Iterable<T> {

	private final Iterator<T> iterator;
	private boolean obtained;
	
	public IteratorWrapper(Iterator<T> iterator) {
		this.iterator = iterator;
		this.obtained = false;
	}
	
	@Override
	public Iterator<T> iterator() {
		if (obtained) {
			throw new IllegalStateException("Iterator already retrieved");
		}
		obtained = true;
		return iterator;
	}
	
}
