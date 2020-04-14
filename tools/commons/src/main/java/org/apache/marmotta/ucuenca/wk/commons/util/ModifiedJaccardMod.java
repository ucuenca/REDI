/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.marmotta.ucuenca.wk.commons.util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.simmetrics.StringMetricBuilder.with;
import org.simmetrics.metrics.JaroWinkler;
import org.simmetrics.metrics.Levenshtein;
import org.simmetrics.simplifiers.Simplifiers;
import org.simmetrics.simplifiers.Soundex;

/**
 *
 * @author cedia
 */
public class ModifiedJaccardMod {

    public boolean shortStrings = false;
    public boolean soundexBoost = false;
    public boolean prioritizeWordOrder = false;
    public boolean onlyCompleteMatchs = false;
    public double syntacticThreshold = 0.89;
    public double abvPenalty = 0.95;
    public static int abvThreshold = 3;
    public boolean applyMinOverlapConstrain = false;
    public double minOverlap = 0.7;
    public int minMatchs = 3;
    public boolean priorFirst = false;
    public boolean priorOrd = false;

    public double countUniqueTokens(String name) {
        List<String> tokenizer = tokenizer(name.toLowerCase());
        Set<String> s = new HashSet<>();
        for (String sx : tokenizer) {
            s.add(sx);
        }
        double r = 0;
        for (String sx : s) {
            if (sx.length() > abvThreshold) {
                r += 1.0;
            } else {
                r += 0.9;
            }
        }
        return r;
    }

    public Map.Entry<Integer, Double> distanceName(String name1, String name2) {
        List<String> tks1 = tokenizer(name1.toLowerCase());
        String firstToken = tks1.isEmpty() ? "" : tks1.get(0);
        boolean firstTokenUsed = true;
        List<String> tks2 = tokenizer(name2.toLowerCase());
        int maxlen = Math.max(tks1.size(), tks2.size());
        onlyCompleteMatchs = true;
        Map.Entry<Integer, Double> c = countMatchs(tks1, tks2);
        Integer completeMatchs = c.getKey();
        onlyCompleteMatchs = false;
        Map.Entry<Integer, Double> c1 = countMatchs(tks1, tks2);
        if (tks1.contains(firstToken)) {
            firstTokenUsed = false;
        }
        double mx = Math.min(tks1.size(), tks2.size());
        double val = (c.getValue() + c1.getValue()) / (c.getKey() + c1.getKey() + mx);
        if (applyMinOverlapConstrain) {
            double rat = (c.getKey() + c1.getKey() + 0.0) / (maxlen + 0.0);
            if (completeMatchs >= minMatchs && rat >= minOverlap) {
            } else {
                val = 0.5 * val;
            }
        }
        if (priorOrd && !firstTokenUsed) {
            val = 0;
        }
        return new AbstractMap.SimpleEntry<>(completeMatchs, val);
    }

    @SuppressWarnings("PMD")
    private Map.Entry<Integer, Double> countMatchs(List<String> tokens1, List<String> tokens2) {
        double sumSimilarity = 0;
        int countMatchs = 0;
        List<Integer> usedTokens1 = new ArrayList<>();
        List<Integer> usedTokens2 = new ArrayList<>();
        for (int i = 0; i < tokens1.size(); i++) {
            for (int j = 0; j < tokens2.size(); j++) {
                if (!usedTokens1.contains(i) && !usedTokens2.contains(j)) {
                    String token1 = tokens1.get(i);
                    String token2 = tokens2.get(j);
                    double sim = syntacticSim(token1, token2);
                    boolean fullTokens = (token1.length() > abvThreshold && token2.length() > abvThreshold);
                    boolean startsw = priorFirst ? token1.startsWith(token2) : token1.startsWith(token2) || token2.startsWith(token1);
                    boolean condFullMatch = false;
                    boolean condAbvMatch = false;
                    if (fullTokens) {
                        condFullMatch = sim >= syntacticThreshold;
                    } else {
                        condAbvMatch = startsw;
                    }
                    if (condAbvMatch) {
                        sim = abvPenalty;
                    }
                    if (onlyCompleteMatchs && condFullMatch || !onlyCompleteMatchs && condAbvMatch) {
                        int maxTkSz = Math.min(tokens1.size(), tokens2.size());
                        double ix = maxTkSz - i;
                        double jx = maxTkSz - j;
                        ix = Math.abs(ix / (maxTkSz + 0.0));
                        jx = Math.abs(jx / (maxTkSz + 0.0));
                        double ij = Math.abs(Math.min(ix, jx) / Math.max(ix, jx));
                        sumSimilarity += prioritizeWordOrder ? ij * sim : sim;
                        countMatchs++;
                        usedTokens1.add(i);
                        usedTokens2.add(j);
                    }
                }
            }
        }
        Collections.sort(usedTokens1, Collections.reverseOrder());
        Collections.sort(usedTokens2, Collections.reverseOrder());

        for (int i : usedTokens1) {
            tokens1.remove(i);
        }
        for (int i : usedTokens2) {
            tokens2.remove(i);
        }

        return new AbstractMap.SimpleEntry<>(countMatchs, sumSimilarity);
    }

    public double syntacticSim(String t1, String t2) {
        double boost = 1.0;
        try {
            double minlen = Math.min(t1.length(), t2.length());
            double fx = 0.05 * (1.0 / (1.0 + Math.exp(-1.25 * minlen + 5.0)));
            Soundex sm = new Soundex();
            boolean equals = sm.simplify(
                    Simplifiers.removeDiacritics()
                            .simplify(t1))
                    .equals(sm.simplify(
                            Simplifiers.removeDiacritics()
                                    .simplify(t2)));
            boost = soundexBoost & equals ? 1.0 + fx : 1.0;
        } catch (Exception e) {
        }
        double sim = 0;
        if (shortStrings) {
            sim = with(new JaroWinkler()).simplify(Simplifiers.removeDiacritics()).build().compare(t2, t1);
        } else {
            sim = (with(new Levenshtein()).simplify(Simplifiers.removeDiacritics()).build().compare(t2, t1)
                    + 2 * with(new JaroWinkler()).simplify(Simplifiers.removeDiacritics()).build().compare(t2, t1)) / 3;
        }

        double val = boost * sim;
        return val > 1.0 ? 1.0 : val;
    }

    public static String specialCharactersClean(String n) {
        return n.replaceAll("\\.|,|;|:|-|\n|\\\\|\\||\"|\'|_|/", " ");
    }

    public List<String> tokenizer(String n) {
        n = specialCharactersClean(n);
        String[] tokens = n.split(" ");
        List<String> list = new ArrayList<>(Arrays.asList(tokens));
        list.removeAll(Arrays.asList("", null));
        return list;
    }
}
