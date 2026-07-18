package com.lil.safetagv2rppsservice.event;

import java.util.UUID;

public record AddressGeocodedEvent(
        UUID addressId,
        Double latitude,
        Double longitude,
        String street,
        String zipCode,
        String city
) {}
