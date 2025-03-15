package com.hsbc.interview.enums;
/**
 * 商户枚举
 * @author wangwei
 * @date 2025-03-15
 */
public enum MerchantEnum {
    MERCHANT_A1("1", "商户1"),
    MERCHANT_A2("2", "商户2"),
    MERCHANT_A3("3", "商户3");

    private String code;
    private String desc;

    MerchantEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(String Code){
        for(MerchantEnum merchantEnum : MerchantEnum.values()){
            if(merchantEnum.code.equals(Code)){
                return merchantEnum.desc;
            }
        }
        return null;
    }


}
