package com.example.rsq.location.model

enum class LocationReadiness {
    NOT_DETERMINED,
    PERMISSION_DENIED,
    SERVICES_DISABLED,
    ACQUIRING,
    LOW_ACCURACY,
    READY,
    ERROR
}
