package com.danzig.crashreport.model;

import java.io.Serializable;

public class CrashReport implements Serializable {

    public String app_name;
    public String client_name;
    public String time;
    public String android_version;
    public String app_version;
    public String device_info;
    public String crash_report;
    public String app_code;
}