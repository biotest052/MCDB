package com.decacagle.items;

public class DataRow {

    public int index, lastIndex, nextIndex;
    public String object;

    public DataRow(int index, int lastIndex, int nextIndex, String object) {
        this.index = index;
        this.lastIndex = lastIndex;
        this.nextIndex = nextIndex;
        this.object = object;
    }

    // Structure: {lastIndex},{nextIndex}::{jsonObj}
    public String toASCII() {
        return index + "," + lastIndex + "," + nextIndex + "::" + object;
    }

    public void setLastIndex(int lastIndex) {
        this.lastIndex = lastIndex;
    }

    public void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getObjectWithId() {
        return "{\"id\":" + index + "," + object.substring(1);
    }

    public static DataRow stringToDataRow(String input) {
        if (!input.isEmpty()) {
            if (input.contains(",") && input.contains("::")) {
                String[] separated = input.split("::");
                String[] indexes = separated[0].split(",");
                String obj = separated[1];

                int idx = Integer.parseInt(indexes[0]);
                int last = Integer.parseInt(indexes[1]);
                int next = Integer.parseInt(indexes[2]);

                return new DataRow(idx, last, next, obj);

            }
        }
        return null;
    }

}
