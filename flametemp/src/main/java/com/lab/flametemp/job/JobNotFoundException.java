package com.lab.flametemp.job;

/** Requested job id does not exist. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(long id) {
        super("no job with id " + id);
    }
}
