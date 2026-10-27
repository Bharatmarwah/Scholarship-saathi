package com.bharat.scholarship_rag_backend.entity;

/**
 * How a student submits an application and how money reaches them. Every
 * supported scheme applies online through a portal, so the value is recorded
 * for completeness of the source facts rather than for routing.
 */
public enum ApplicationMode {

    /** Submitted online on the National Scholarship Portal. */
    ONLINE_PORTAL,

    /** Submitted to the administering department directly, per the guidelines. */
    DEPARTMENT_DIRECT
}