package com.boatticket.update;

/** Immutable description of a verified newer application release. */
public record UpdateInfo(String version, String installerUrl, String sha256) {}
