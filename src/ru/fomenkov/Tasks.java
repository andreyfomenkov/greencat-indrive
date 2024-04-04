package ru.fomenkov;

import java.io.*;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.StringTokenizer;

public class Tasks {

    private static class Set {
        final String[] values;

        public Set(String[] values) {
            this.values = values;
        }
    }

    private static class Signature {
        final int[] values;

        public Signature(int[] values) {
            this.values = values;
        }

        public java.util.Set<Integer> toHashSet() {
            java.util.Set<Integer> set = new HashSet<>();

            for (int value : values) {
                set.add(value);
            }
            return set;
        }
    }

    private static final int[] mercen = new int[] {
            2, 3, 5, 7, 13, 17, 19, 31, 61, 89, 107, 127, 521, 607, 1279, 2203, 2281, 3217, 4253, 4423, 9689,
            9941, 11213, 19937, 21701, 23209, 44497, 86243, 110503, 132049, 216091, 756839, 859433, 1257787,
            1398269, 2976221, 3021377, 6972593, 13466917, 20996011, 24036583, 25964951, 30402457, 32582657,
            37156667, 42643801, 43112609, 57885161, 74207281, 77232917, 82589933
    };

    private static int getHash(String str, int k) {
        int m = mercen[k];
        BigInteger value = BigInteger.ZERO;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            BigInteger biA = BigInteger.valueOf(c - 33);
            BigInteger biB = BigInteger.valueOf(m).pow(str.length() - i - 1);
            value = value.add(biA.multiply(biB));
        }
        value = value.multiply(BigInteger.valueOf(m));
        return value.intValue();
    }

    private static Signature getSignature(Set set) {
        int[] sign = new int[mercen.length];

        for (int k = 0; k < sign.length; k++) {
            sign[k] = getMinHash(set, k);
        }
        return new Signature(sign);
    }

    private static int getMinHash(Set set, int k) {
        int minHashValue = Integer.MAX_VALUE;

        for (String e : set.values) {
            int hashValue = getHash(e, k);

            if (hashValue < minHashValue) {
                minHashValue = hashValue;
            }
        }
        return minHashValue;
    }

    private static Set[] getTestBSets() {
        return new Set[] {
                new Set(new String[] { "10", "20", "30" } ),
                new Set(new String[] { "10", "20", "40" } ),
                new Set(new String[] { "10", "20", "50" } ),
                new Set(new String[] { "30", "40", "50" } ),
                new Set(new String[] { "20", "40", "50", "60", "70", "80", "90" } ),
                new Set(new String[] { "80", "80", "80", "80", "80", "80", "80" } ),
                new Set(new String[] { "90" } ),
                new Set(new String[] { "190" } ),
                new Set(new String[] { "120", "4143", "134" } ),
                new Set(new String[] { "1", "2", "1000" } ),
        };
    }

    private static Set[] getTestASets() {
        return new Set[] {
                new Set(new String[] { "1", "2", "3" } ),
                new Set(new String[] { "10", "20", "30" } ),
                new Set(new String[] { "20", "40", "50" } ),
                new Set(new String[] { "90" } ),
                new Set(new String[] { "80", "90" } ),
        };
    }

    private static String getMatch(Signature signA, Signature signB) {
        java.util.Set<Integer> setA = signA.toHashSet();
        java.util.Set<Integer> setB = signB.toHashSet();

        int startSize = setA.size();
        setA.removeAll(setB);
        int endSize = setA.size();
        float result = (float) (startSize - endSize) / startSize;
        return String.format("%.3f", result);
    }

    /**
     * 0.000 0.000 0.000 0.000 0.000 0.000 0.000 0.000 0.000 0.471
     * 1.000 0.647 0.725 0.098 0.039 0.000 0.000 0.000 0.000 0.000
     * 0.059 0.294 0.216 0.314 0.647 0.000 0.000 0.000 0.000 0.000
     * 0.000 0.000 0.000 0.000 0.059 0.000 1.000 0.000 0.000 0.000
     * 0.000 0.000 0.000 0.000 0.176 0.765 0.235 0.000 0.000 0.000
     */
    public static void main(String[] args) {
        Set[] bSets = null;
        Set[] aSets = null;
        bSets = getTestBSets();
        aSets = getTestASets();

        try {
//            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//            // B
//            StringTokenizer st = new StringTokenizer(br.readLine());
//            int n = Integer.parseInt(st.nextToken());
//            bSets = new Set[n];
//
//            for (int i = 0; i < n; i++) {
//                String line = br.readLine();
//                String[] values = line.split(" ");
//                bSets[i] = new Set(values);
//            }
//            // A
//            st = new StringTokenizer(br.readLine());
//            int m = Integer.parseInt(st.nextToken());
//            aSets = new Set[m];
//
//            for (int i = 0; i < m; i++) {
//                String line = br.readLine();
//                String[] values = line.split(" ");
//                aSets[i] = new Set(values);
//            }
            // Calculate
            Signature[] bSigns = new Signature[bSets.length];
            Signature[] aSigns = new Signature[aSets.length];

            for (int i = 0; i < bSets.length; i++) {
                bSigns[i] = getSignature(bSets[i]);
            }
            for (int i = 0; i < aSets.length; i++) {
                aSigns[i] = getSignature(aSets[i]);
            }
            String line = "";

            for (int i = 0; i < aSigns.length; i++) {
                for (int j = 0; j < bSigns.length; j++) {
                    Signature signA = aSigns[i];
                    Signature signB = bSigns[j];
                    String value = getMatch(signA, signB);
                    line += value + " ";
                }
                line += "\n";
            }
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
            bw.write(line);
            bw.flush();
        } catch (IOException e) {
        }
    }
}