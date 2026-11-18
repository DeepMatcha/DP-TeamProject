package com.holub.life.api;
@FunctionalInterface
public interface HostErrorListener {
    void onError(HostError error);
}
