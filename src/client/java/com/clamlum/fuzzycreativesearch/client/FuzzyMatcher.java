package com.clamlum.fuzzycreativesearch.client;

public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    public static int fuzzyScore(String query, String target) {
        query = normalize(query);
        target = normalize(target);

        if (query.isEmpty()) return 0;

        String[] qWords = query.split("\\s+");
        String[] tWords = target.split("\\s+");

        int score = 0;
        boolean[] used = new boolean[tWords.length];

        for (String qw : qWords) {
            int best = Integer.MAX_VALUE;
            int bestIndex = -1;

            for (int i = 0; i < tWords.length; i++) {
                if (used[i]) continue;
                int s = wordScore(qw, tWords[i]);
                if (s < best) {
                    best = s;
                    bestIndex = i;
                }
            }

            if (bestIndex == -1 || best > 4) {
                return -1;
            }

            used[bestIndex] = true;
            score += best;
        }

        score += (tWords.length - qWords.length) * 2;

        return score;
    }

    private static int wordScore(String a, String b) {
        if (b.contains(a)) return 0;
        return levenshtein(a, b);
    }

    public static String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}