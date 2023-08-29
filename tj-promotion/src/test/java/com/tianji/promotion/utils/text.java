package com.tianji.promotion.utils;

public class text {

    public static String compareStrings(String s1, String s2) {
    int i = 0, j = 0;
    StringBuilder result = new StringBuilder();

    while (i < s1.length() && j < s2.length()) {
        if (s1.charAt(i) == s2.charAt(j)) { // 如果字符相同，则同时移动两个指针
            i++;
            j++;
        } else {
            // 检查s2的下一个字符是否与s1的当前字符匹配
            if (i + 1 < s1.length() && s2.charAt(j) == s1.charAt(i + 1)) {
                result.append("位置").append(i).append("缺少").append(s1.charAt(i)).append(" ");
                i++;
            }
            // 检查s1的下一个字符是否与s2的当前字符匹配
            else if (j + 1 < s2.length() && s1.charAt(i) == s2.charAt(j + 1)) {
                result.append("位置").append(i).append("多出").append(s2.charAt(j)).append(" ");
                j++;
            }
            // 字符不同且不是因为省略造成的
            else {
                result.append("位置").append(i).append("错误 应为").append(s1.charAt(i)).append(" ");
                i++;
                j++;
            }
        }
    }

    while (i < s1.length()) { // 如果s1还有剩余字符
        result.append("位置").append(i).append("缺少").append(s1.charAt(i)).append(" ");
        i++;
    }

    while (j < s2.length()) { // 如果s2还有剩余字符
        result.append("位置").append(i).append("多出").append(s2.charAt(j)).append(" ");
        j++;
        i++;
    }

    return result.toString().trim();
}

    public static void main(String[] args) {
        String s1 = "abcdefg";
        String s2 = "25abdfxx";
        System.out.print(compareStrings(s1, s2));
    }

}
