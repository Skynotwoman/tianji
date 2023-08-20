package com.tianji.promotion.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeUtilTest {

    @Test
    void testCodeUtil(){
        String code = CodeUtil.generateCode(1, 1000);
        System.out.println("code = " + code);

    }
}