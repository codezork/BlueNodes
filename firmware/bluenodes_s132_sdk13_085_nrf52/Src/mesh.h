/***********************************************************************************
Copyright (c) Bluenodes GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are not permitted.
************************************************************************************/

#ifndef MESH_H_INCLUDED
#define MESH_H_INCLUDED

/** mesh handles **/
#define BRIGHTNESS_CHARACTERISTIC_HANDLE				1
#define BUTTON_CHARACTERISTIC_HANDLE					2
#define REQUEST_BRIGHTNESS_CHARACTERISTIC_HANDLE		3
#define REQUEST_DEVICEADDRESS_CHARACTERISTIC_HANDLE		4
#define ANSWER_BRIGHTNESS_CHARACTERISTIC_HANDLE			5
#define ANSWER_DEVICEADDRESS_CHARACTERISTIC_HANDLE		6
#define NOTIFY_DEVICE_ERROR_CHARACTERISTIC_HANDLE		7
#define SET_TIME_CHARACTERISTIC_HANDLE					8
#define CLEAR_STORAGE_CHARACTERISTIC_HANDLE				9
#define REQUEST_STORAGE_CHARACTERISTIC_HANDLE			10
#define ANSWER_STORAGE_CHARACTERISTIC_HANDLE			11
#define MESSAGE_CHARACTERISTIC_HANDLE                   12
/** mesh factory setting parameters **/
#define MAX_MESH_CONTENT        23
#define MESH_ACCESS_ADDR        (0xA541A68F)
#define MESH_INTERVAL_MIN_MS    (100)
#define MESH_CHANNEL            (38)
#define MESH_CLOCK_SOURCE       {.source        = NRF_CLOCK_LF_SRC_XTAL,            \
                                 .rc_ctiv       = 0,                                \
                                 .rc_temp_ctiv  = 0,                                \
                                 .xtal_accuracy = NRF_CLOCK_LF_XTAL_ACCURACY_75_PPM}

#define INVALID_HANDLE          (RBC_MESH_INVALID_HANDLE)

typedef struct
{
    uint8_t handle;
	uint8_t value[MAX_MESH_CONTENT];
	uint8_t size;
} mesh_value_t;

void send_mesh_value(uint8_t characteristic, uint8_t *in, uint8_t size, bool with_address);

#endif /* MESH_H_INCLUDED */
