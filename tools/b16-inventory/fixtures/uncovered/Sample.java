public final class Sample {
    public static double defaults() {
        double balance = 100.0;
        double untracked = 5.0; // this literal must become a GAP
        int hex = 0x10; // integral hexadecimal literals must also be covered
        String note = "99.0 is not a Java numeric literal";
        /* 88.0 is also not a Java numeric literal */
        return balance + untracked + note.length() * 0.0;
    }
}
