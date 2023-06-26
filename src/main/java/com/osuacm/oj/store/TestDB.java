package com.osuacm.oj.store;

import org.springframework.boot.autoconfigure.r2dbc.R2dbcConnectionDetails;
import org.springframework.stereotype.Component;

@Component
public class TestDB {

    public TestDB(R2dbcConnectionDetails connectionDetails){
        System.out.println("Testing...");
        System.out.println(connectionDetails);
    }

}
