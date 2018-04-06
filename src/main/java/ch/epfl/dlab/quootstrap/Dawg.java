package ch.epfl.dlab.quootstrap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import scala.Tuple2;

/**
 * This class implements a Directed Acyclic Word Graph (DAWG),
 * also known as deterministic acyclic finite state automaton (DAFSA).
 */
public class Dawg {

	final Node root;
	final Multimap<String, Node> allNodes;
	
	public Dawg() {
		root = new Node("");
		allNodes = HashMultimap.create();
	}
	
	public Node getRoot() {
		return root;
	}
	
	public static Optional<Pattern> convert(List<Token> newPattern) {
		// Remove leading/trailing asterisks
		int startPos = 0;
		while (newPattern.size() > startPos && newPattern.get(startPos).getType() == Token.Type.ANY) {
			startPos++;
		}
		
		int endPos = newPattern.size();
		while (endPos > 0 && newPattern.get(endPos - 1).getType() == Token.Type.ANY) {
			endPos--;
		}
		
		if (startPos >= endPos) {
			return Optional.empty();
		}
		
		newPattern = newPattern.subList(startPos, endPos);
		
		newPattern.replaceAll(x -> {
				if (x.toString().equals(Pattern.QUOTATION_PLACEHOLDER)) {
					return new Token(null, Token.Type.QUOTATION);
				} else if (x.toString().equals(Pattern.SPEAKER_PLACEHOLDER)) {
					return new Token(null, Token.Type.SPEAKER);
				}
				return x;
			});
		
		try {
			Pattern p = new Pattern(newPattern);
			
			// Add a constraint on the number of consecutive $* tokens
			int maxCount = 0;
			final int threshold = 5;
			for (Token t : p.getTokens()) {
				if (t.getType() == Token.Type.ANY) {
					maxCount++;
				} else {
					maxCount = 0;
				}
				if (maxCount > threshold) {
					return Optional.empty();
				}
			}
			
			return Optional.of(p);
		} catch (Exception e) {
			// Discard invalid patterns
			return Optional.empty();
		}
	}
	
	public void addAll(List<List<String>> patterns) {
		patterns.sort((x, y) -> {
			for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
				int comp = x.get(i).compareTo(y.get(i));
				if (comp != 0) {
					return comp;
				}
			}
			return Integer.compare(x.size(), y.size());
		});
		patterns.forEach(this::add);
		if (root.children.size() > 0) {
			addOrReplace(root);
		}
	}
	
	private void add(List<String> tokens) {
		Tuple2<Node, List<String>> tail = getInsertionPoint(tokens);
		if (tail._1 == null) {
			return;
		}
		if (tail._1.children.size() > 0) {
			addOrReplace(tail._1);
		}
		addSuffix(tail._1, tail._2);
	}
	
	private Tuple2<Node, List<String>> getInsertionPoint(List<String> tokens) {
		Node node = root;
		for (int i = 0; i < tokens.size(); i++) {
			node.count++;
			
			String token = tokens.get(i);
			Node next = node.children.get(token);
			if (next == null) {
				return new Tuple2<>(node, tokens.subList(i, tokens.size()));
			}
			node = next;
		}
		return new Tuple2<>(null, Collections.emptyList());
	}
	
	/**
	 * Export this graph into a GraphViz description (for rendering).
	 * @return a string in GraphViz format
	 */
	public String toDot() {
		StringBuilder builder = new StringBuilder();
		builder.append("digraph g{\n\trankdir=LR\n");
		
		allNodes.put("", root);
		allNodes.entries().forEach(x -> {
			builder.append("\t" + System.identityHashCode(x.getValue())
			+ " [label=\"" + x.getKey() + " ["+ x.getValue().count + "]\"]\n");
		});
		allNodes.values().forEach(x -> {
			x.children.values().forEach(y -> {
				builder.append("\t" + System.identityHashCode(x) + " -> " + System.identityHashCode(y) + "\n");
			});
		});
		allNodes.remove("", root);
		builder.append("}\n");
		return builder.toString();
	}
	
	private void addSuffix(Node node, List<String> tokens) {
		for (String token : tokens) {
			Node next = new Node(token);
			next.count++;
			node.children.put(token, next);
			node.lastAdded = next;
			node = next;
		}
	}
	
	private void addOrReplace(Node node) {
	    Node child = node.lastAdded;
	    if (child.children.size() > 0) {
	        addOrReplace(child);
	    }

	    Collection<Node> candidateChildren = allNodes.get(child.word);
	    for (Node existingChild : candidateChildren) {
	    	if (existingChild.equals(child)) {
	    		existingChild.count += child.count;
	    		node.lastAdded = existingChild;
	    		node.children.put(child.word, existingChild);
	    		return;
	    	}
	    }
	    
	    allNodes.put(child.word, child);
	}
	
	public static class Node {
		private final String word;
		private final Map<String, Node> children;
		private int count;
		private Node lastAdded;
		
		public Node(String word) {
			this.word = word;
			this.children = new HashMap<>();
			this.count = 0;
		}
		
		public String getWord() {
			return word;
		}
		
		public int getCount() {
			return count;
		}
		
		public Collection<Node> getNodes() {
			return children.values();
		}

		@Override
		public int hashCode() {
			int hash = word.hashCode();
			// Commutative hash function
			for (Map.Entry<String, Node> entry : children.entrySet()) {
				hash ^= entry.getKey().hashCode() + 31 * System.identityHashCode(entry.getValue());
			}
			return hash;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			
			if (obj instanceof Node) {
				final Node node = (Node) obj;
				return hashCode() == node.hashCode() && word.equals(node.word) && equalsChildren(node);
			}
			
			return false;
		}
		
		@Override
		public String toString() {
			return Integer.toHexString(System.identityHashCode(this)) + "(" + word + ", " + count + ")";
		}
		
		private boolean equalsChildren(final Node other) {
			if (children.size() != other.children.size()) {
				return false;
			}
			
			for (Map.Entry<String, Node> entry : children.entrySet()) {
				Node match = other.children.get(entry.getKey());
				// The pointer comparison is intentional!
				if (match == null || match != entry.getValue()) {
					return false;
				}
			}
			
			return true;
		}
		
		/**
		 * Extract all patterns stored in this graph.
		 * @param all The output list.
		 * @param current Temporary storage (must be empty).
		 */
		public void dump(List<List<Node>> all, List<Node> current) {
			current.add(this);
			if (children.isEmpty()) {
				all.add(new ArrayList<>(current));
			} else {
				children.values().forEach(x -> x.dump(all, current));
			}
			current.remove(current.size() - 1);
		}
		
	}
}
