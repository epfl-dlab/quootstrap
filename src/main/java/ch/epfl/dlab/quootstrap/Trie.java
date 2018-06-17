package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Trie implements Serializable {

	private static final long serialVersionUID = -9164247688163451454L;
	
	private final Node rootNode;
	private final boolean caseSensitive;
	
	public Trie(Collection<Pattern> patterns, boolean caseSensitive) {
		this.rootNode = new RootNode(caseSensitive);
		this.caseSensitive = caseSensitive;
		List<Pattern> sortedPatterns = new ArrayList<>(patterns);
		sortedPatterns.sort(Collections.reverseOrder());
		sortedPatterns.forEach(this::insertPattern);
	}
	
	private void insertPattern(Pattern pattern) {
		Node current = rootNode;
		
		Iterator<Token> it = pattern.iterator();
		while (it.hasNext()) {
			Token token = it.next();
			Node next = null;
			
			if (token.getType() == Token.Type.GENERIC) {
				// Text token
				String key = caseSensitive
						? token.toString()
						: token.toString().toLowerCase(Locale.ROOT);
				next = current.getTextChild(key);
				if (next == null) {
					next = it.hasNext()
							? new InnerNode(token, caseSensitive)
							: new TerminalNode(token, pattern.getConfidenceMetric());
					((RootNode) current).textChildren.put(key, next);
				}
			} else {
				// Special token
				for (Node candidateNext : current) {
					if (candidateNext.getToken().equals(token)) {
						next = candidateNext;
						break;
					}
				}
				if (next == null) {
					next = it.hasNext()
							? new InnerNode(token, caseSensitive)
							: new TerminalNode(token, pattern.getConfidenceMetric());
					((RootNode) current).children.add(next);
				}
			}
			
			current = next;
		}
	}
	
	public boolean isCaseSensitive() {
		return caseSensitive;
	}
	
	public List<Pattern> getAllPatterns() {
		List<Pattern> allPatterns = new ArrayList<>();
		
		List<Token> currentPattern = new ArrayList<>();
		DFS(rootNode, currentPattern, allPatterns);
		return allPatterns;
	}
	
	public Node getRootNode() {
		return rootNode;
	}
	
	private void DFS(Node current, List<Token> currentPattern, List<Pattern> allPatterns) {
		if (current.hasChildren()) {
			for (Node next : current.getTextChildren()) {
				currentPattern.add(next.getToken());
				DFS(next, currentPattern, allPatterns);
				currentPattern.remove(currentPattern.size() - 1);
			}
			
			for (Node next : current) {
				currentPattern.add(next.getToken());
				DFS(next, currentPattern, allPatterns);
				currentPattern.remove(currentPattern.size() - 1);
			}
		} else {
			// Terminal node
			allPatterns.add(new Pattern(new ArrayList<>(currentPattern), current.getConfidenceFactor()));
		}
	}
	
	public interface Node extends Iterable<Node>, Serializable {
		Token getToken();
		boolean hasChildren();
		Node getTextChild(String key);
		Iterable<Node> getTextChildren();
		double getConfidenceFactor();
	}
	
	private static class RootNode implements Node {

		private static final long serialVersionUID = -3680161357395993244L;
		
		private final Map<String, Node> textChildren;
		private final List<Node> children;
		private final boolean caseSensitive;
		
		public RootNode(boolean caseSensitive) {
			this.children = new ArrayList<>();
			this.textChildren = new HashMap<>();
			this.caseSensitive = caseSensitive;
		}
		
		@Override
		public Iterator<Node> iterator() {
			return children.iterator();
		}

		@Override
		public Token getToken() {
			throw new UnsupportedOperationException("No token in the root node");
		}

		@Override
		public boolean hasChildren() {
			return !children.isEmpty() || !textChildren.isEmpty();
		}
		
		@Override
		public String toString() {
			return "*ROOT*";
		}

		@Override
		public double getConfidenceFactor() {
			throw new UnsupportedOperationException("This is not a terminal node");
		}

		@Override
		public Node getTextChild(String key) {
			if (!caseSensitive) {
				key = key.toLowerCase(Locale.ROOT);
			}
			return textChildren.get(key);
		}

		@Override
		public Iterable<Node> getTextChildren() {
			return textChildren.values();
		}
	}
	
	private static class InnerNode extends RootNode {
		
		private static final long serialVersionUID = 8115648454995183879L;
		
		private final Token token;
		
		private InnerNode(Token t, boolean caseSensitive) {
			super(caseSensitive);
			token = t;
		}
		
		public Token getToken() {
			return token;
		}
		
		@Override
		public String toString() {
			return token.toString();
		}
	}
	
	private static class TerminalNode implements Node {
		
		private static final long serialVersionUID = 6535993027251722667L;
		
		private final Token token;
		private final double confidenceFactor;
		
		public TerminalNode(Token t, double confidence) {
			token = t;
			confidenceFactor = confidence;
		}

		@Override
		public Iterator<Node> iterator() {
			return Collections.emptyIterator();
		}

		@Override
		public Token getToken() {
			return token;
		}

		@Override
		public boolean hasChildren() {
			return false;
		}
		
		@Override
		public String toString() {
			return token.toString();
		}

		@Override
		public double getConfidenceFactor() {
			return confidenceFactor;
		}
		
		@Override
		public Node getTextChild(String key) {
			return null;
		}

		@Override
		public Iterable<Node> getTextChildren() {
			return Collections.emptyList();
		}
	}
}
