package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public class HashTrie implements Serializable {

	private static final long serialVersionUID = -9164247688163451454L;
	
	private final Node rootNode;
	private final boolean caseSensitive;
	
	public HashTrie(Iterable<List<String>> substrings, boolean caseSensitive) {
		this.rootNode = new Node(null, caseSensitive);
		this.caseSensitive = caseSensitive;
		substrings.forEach(this::insertSubstring);
	}
	
	private void insertSubstring(List<String> substring) {
		Node current = rootNode;
		
		for (int i = 0; i < substring.size(); i++) {
			String token = substring.get(i);
			String key = caseSensitive ? token : token.toLowerCase(Locale.ROOT);
			
			Node next = current.findChild(key);
			if (next == null) {
				next = new Node(token, caseSensitive);
				current.children.put(key, next);
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
		private final boolean caseSensitive;
		private final String value;
		private boolean terminal;
		
		public Node(String value, boolean caseSensitive) {
			this.children = new HashMap<>();
			this.caseSensitive = caseSensitive;
			this.value = value;
			this.terminal = false;
		}
		
		public boolean hasChildren() {
			return !children.isEmpty();
		}
		
		public Node findChild(String token) {
			return children.get(caseSensitive ? token : token.toLowerCase(Locale.ROOT));
		}
		
		public boolean isTerminal() {
			return terminal;
		}
		
		public String getValue() {
			return value;
		}

		@Override
		public Iterator<Entry<String, Node>> iterator() {
			return Collections.unmodifiableMap(children).entrySet().iterator();
		}
	}
}
