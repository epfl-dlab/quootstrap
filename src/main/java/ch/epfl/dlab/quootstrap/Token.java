package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

public final class Token implements Serializable, Comparable<Token> {

	private static final long serialVersionUID = 160449932354710762L;

	public enum Type {
		GENERIC, QUOTATION, SPEAKER, ANY;
	}
	
	private final String text;
	private final Type type;
	
	public Token(String text, Type type) {
		this.text = text;
		this.type = type;
	}
	
	public final Type getType() {
		return this.type;
	}
	
	public static List<String> getStrings(Collection<Token> tokens) {
		return tokens.stream()
			.map(x -> x.text)
			.collect(Collectors.toList());
	}
	
	public static List<Token> getTokens(Collection<String> tokens) {
		return tokens.stream()
			.map(x -> new Token(x, Type.GENERIC))
			.collect(Collectors.toList());
	}

	@Override
	public int hashCode() {
		int hashCode = Objects.hashCode(text);
		
		// Important, since the JVM uses the object hash code for enums
		switch (type) {
		case GENERIC:
			hashCode += 377280272;
			break;
		case QUOTATION:
			hashCode += 116811174;
			break;
		case SPEAKER:
			hashCode += 637353343;
			break;
		case ANY:
			hashCode += 265741433;
			break;
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Token) {
			Token t = (Token) obj;
			return type == t.type && Objects.equals(text, t.text);
		}
		return false;
	}
	
	public boolean equalsIgnoreCase(Token t) {
		if (t == null) {
			return false;
		}
		
		return type == t.type && text.equalsIgnoreCase(t.text);
	}
	
	public static List<Token> caseFold(List<Token> tokens) {
		return tokens.stream()
			.map(x -> {
				if (x.text != null) {
					String s = x.text.toLowerCase(Locale.ROOT);
					return new Token(s, x.type);
				}
				return x;
			})
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	@Override
	public String toString() {
		if (text == null && type == Type.QUOTATION) {
			return Pattern.QUOTATION_PLACEHOLDER;
		}
		if (text == null && type == Type.SPEAKER) {
			return Pattern.SPEAKER_PLACEHOLDER;
		}
		if (text == null && type == Type.ANY) {
			return Pattern.ANY_PLACEHOLDER;
		}
		return text;
	}

	@Override
	public int compareTo(Token other) {
		// Define a lexicographical order of tokens
		if (type == other.type)
		{
			return ObjectUtils.compare(text, other.text); // Handles nulls
		}
		return type.compareTo(other.type);
	}
	
}
