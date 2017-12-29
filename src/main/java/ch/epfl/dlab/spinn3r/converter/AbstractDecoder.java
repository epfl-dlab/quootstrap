package ch.epfl.dlab.spinn3r.converter;
import java.util.Iterator;

/**
 * Provides a convenient interface to create custom iterators
 * without materializing intermediate results.
 */
public abstract class AbstractDecoder<T> implements Iterator<T> {

	private boolean dirty;
	private boolean finished;
	private T cachedNext;
	
	protected AbstractDecoder() {
		dirty = true;
		finished = false;
	}
	
	/**
	 * Obtain the next element.
	 */
	protected abstract T getNextImpl();
	
	private void extractNext()
	{
		if (!finished && dirty) {
			cachedNext = getNextImpl();
			dirty = false;
			
			if (cachedNext == null) {
				finished = true;
			}
		}
	}
	
	@Override
	public boolean hasNext() {
		extractNext();
		return !finished;
	}

	@Override
	public T next() {
		extractNext();
		T next = cachedNext;
		cachedNext = null;
		dirty = true;
		return next;
	}

}
