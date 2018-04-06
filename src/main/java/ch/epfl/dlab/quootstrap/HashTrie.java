package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class HashTrie implements Serializable {

	private static final long serialVersionUID = -9164247688163451454L;
	
	private final Node rootNode;
	
	public HashTrie(Iterable<List<String>> substrings) {
		rootNode = new Node();
		substrings.forEach(this::insertSubstring);
	}
	
	private void insertSubstring(List<String> substring) {
		Node current = rootNode;
		
		for (int i = 0; i < substring.size(); i++) {
			String token = substring.get(i);
			
			Node next = current.findChild(token);
			if (next == null) {
				next = new Node();
				current.children.put(token, next);
			}
			current = next;
		}

		current.terminal = true;
	}
	
	public List<List<String>> getAllSubstrings() {
		List<List<String>> allSubstrings = new ArrayList<>();
		
		List<String> currentSubstring = new ArrayList<>();
		DFS(rootNode, currentSubstring, allSubstrings);
		return allSubstrings;
	}
	
	public Node getRootNode() {
		return rootNode;
	}
	
	private void DFS(Node current, List<String> currentSubstring, List<List<String>> allSubstrings) {
		if (current.isTerminal()) {
			allSubstrings.add(new ArrayList<>(currentSubstring));
		}
		
		for (Map.Entry<String, Node> next : current) {
			currentSubstring.add(next.getKey());
			DFS(next.getValue(), currentSubstring, allSubstrings);
			currentSubstring.remove(currentSubstring.size() - 1);
		}
	}
	
	public static class Node implements Iterable<Map.Entry<String, Node>>, Serializable {
		
		private static final long serialVersionUID = -4344489198225825075L;
		
		private final Map<String, Node> children;
		private boolean terminal;
		
		public Node() {
			this.children = new HashMap<>();
			this.terminal = false;
		}
		
		public boolean hasChildren() {
			return !children.isEmpty();
		}
		
		public Node findChild(String token) {
			return children.get(token);
		}
		
		public boolean isTerminal() {
			return terminal;
		}

		@Override
		public Iterator<Entry<String, Node>> iterator() {
			return Collections.unmodifiableMap(children).entrySet().iterator();
		}
	}
}
