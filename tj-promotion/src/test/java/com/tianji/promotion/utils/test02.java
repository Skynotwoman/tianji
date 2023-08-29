package com.tianji.promotion.utils;

import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class test02 {
    /*
     *
     *
     * */

    public static void main(String[] args) {
        String s1 = "abcdefg";
        String s2 = "25abdefxx";

        compareString(s1,s2);
    }


    private static void compareString(String s1, String s2) {
        List<Character> list1 = s1.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());
        List<Character> list2 = s2.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());
        //System.out.println(list1);
        //System.out.println(list2);

        int num = 1;
        List<String> s = new ArrayList<>();
        for (int i = 0; i < list1.size(); ) {
            int j;
            for (j = 0; j < list2.size(); ) {
                if (list2.get(j).equals(list1.get(i))) {
                    break;
                } else {
                    s.add(String.valueOf(list2.get(j)));
                    list2.remove(j);
                }
            }
            System.out.println(list2 + "*");
            String result = s.stream().collect(Collectors.joining());
            System.out.println("位置0多出" + result);
            break;
        }
        System.out.println(list1 + "--");
        System.out.println(list2 + "--");

        for (int i2 = 0; i2 < list1.size(); ) {
            for (int j2 = 0; j2 < list2.size(); ) {
                if (list2.get(j2).equals(list1.get(i2))) {
                    i2++;
                    j2++;
                }
                i2++;
                j2++;
                if (list1.get(i2 + 1).equals(list2.get(j2))) {
                    System.out.println("位置" + j2 + "缺少" + list1.get(i2));
                }
                i2 = i2 + 2;
                j2++;
                System.out.println(i2);
                System.out.println(j2);
                if (list2.get(j2).equals(list1.get(i2))){
                    i2++;
                    j2++;
                }
                break;
            }
            break;
        }
    }
}




