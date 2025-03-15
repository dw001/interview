package com.hsbc.interview.dto;

import com.hsbc.interview.entity.Transaction;
import lombok.Data;

@Data
public class TransDataDto extends Transaction {
    String merchantName;
}
