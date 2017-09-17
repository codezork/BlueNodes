/***********************************************************************************
Copyright (c) Bluenodes GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are not permitted.
************************************************************************************/

#ifndef COMMON_H_INCLUDED
#define COMMON_H_INCLUDED

#include "mesh.h"

/** prototypes **/
void message(mesh_value_t *message);
void notify_error(mesh_value_t *message);
void print_data(char *type, uint8_t *address, uint8_t len);
void ble_connect_event_handler(bool connected);
void adc_value_notification_evt_handler(float value, bool isMinute);
#endif /* COMMON_H_INCLUDED */
