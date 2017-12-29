package ch.epfl.dlab.quotation;

import java.util.Random;

import scala.Tuple3;

public class SampleSentenceGenerator {

	private static final String[] QUOTATIONS = {
			"I think the division of power, with Republicans taking the House is a factor, as well,",
			"We knew they couldn't score any more,",
			"Matt's passing took everybody by surprise, and there's definitely a sense of loss and sadness,",
			"It sounded like the woman was going down, so I assumed it was a drowning,",
			"It was very dirty, snow, the salt, the sand, everything,",
			"There's a mix of residents, students, and offices, so we thought there's enough need,",
			"He asked me to keep this confidential,",
			"Prior to our arrival, a family member had already taken a weapon away from him,",
			"Forecasts are often wrong.",
			"I am requesting a meeting for the bus drivers.",
			"I look forward to working with the VIP team and accounts,",
			"To me, it's a three-legged stool and each leg reports to this board,",
			"I've seen forty years of damage as to what these pot laws do,",
			"My face was dripping like lettuce that's just been washed.",
			"Obviously, investors are expecting a lot more of the company."
	};
	
	private static final String[] PATTERNS = {
			"$Q $S said.",
			"$Q said $S.",
			"$S said: $Q",
			"$Q $S said in a statement.",
			"$Q $S told."
	};
	
	private static final String[] FIRST_NAMES = {
			"Derrell", "Jennifer", "George", "Michelle", "Stephen", "Louis", "Aldo", "Patrick", "John", "Mark", "Jake",
			"Josh", "Karen", "Bruce", "Robin", "Janet", "Ellis", "Dale", "Gavin", "Paul", "Holly", "Wayne", "Greg", "Howard"
	};
	
	private static final String[] LAST_NAMES = {
			"Reeves", "Cohen", "Dunn", "Murphy", "Baumgardner", "Meadows", "Kimbrell", "Horwitz", "Sobel", "Rutherford",
			"Spence", "Stender", "Miller", "Accorsi", "Watson", "Carton", "Schultz", "Monroe", "Block", "Baker", "Richard"
	};
	
	private final Random rnd;
	
	public SampleSentenceGenerator() {
		rnd = new Random();
	}
	
	public SampleSentenceGenerator(Random rnd) {
		this.rnd = rnd;
	}
	
	// Tuple: (sentence, speaker, isPositive)
	public Tuple3<String, String, Boolean> generate() {
		boolean positive = rnd.nextBoolean();
		
		String fullName = sample(FIRST_NAMES) + " " + sample(LAST_NAMES);
		String quotation = sample(QUOTATIONS);
		String pattern = sample(PATTERNS);
		
		
		pattern = pattern
				.replace("$Q", "<q>" + quotation + "</q>")
				.replace("$S", fullName);
		
		if (!positive) {
			// Use bogus name
			String newName;
			do {
				newName = sample(FIRST_NAMES) + " " + sample(LAST_NAMES);
			} while (newName.equals(fullName));
			fullName = newName;
		}
		
		return new Tuple3<>(pattern, fullName, positive);
	}
	
	private <T> T sample(T[] array) {
		return array[rnd.nextInt(array.length)];
	}
}
