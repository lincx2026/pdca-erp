package com.pcba.erp.common.aspectj;

/** 数据范围 ThreadLocal, 便于 XML 取值: T(com.pcba.erp.common.aspectj.DataScopeThreadLocal).get() */
public final class DataScopeThreadLocal {
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();
    public static void set(String s) { if (s == null) HOLDER.remove(); else HOLDER.set(s); }
    public static String get() { String s = HOLDER.get(); return s == null ? "" : s; }
    public static void clear() { HOLDER.remove(); }
    public static String getAndClear() { String s = HOLDER.get(); HOLDER.remove(); return s == null ? "" : s; }
}
