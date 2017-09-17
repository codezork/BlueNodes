/***********************************************************************************
Copyright (c) Bluenodes GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are not permitted.
************************************************************************************/

#ifndef SENSOR_H__
#define SENSOR_H__
#ifdef CURRENTSENSOR
#include "nrf_drv_gpiote.h"
#include "nrf_drv_spi.h"
#include "nrf_timer.h"
#include "rbc_mesh.h"
#include "config.h"

/** the pins **/
#define OCD_PIN  7            // over current signal.
#define SPIM0_SS_PIN    8     // SPI CSN signal.
#define SPIM0_SCK_PIN   9     // SPI SCK signal.
#define SPIM0_MISO_PIN  10    // SPI MISO signal.

/** current sensor message code to host **/
#define CSERROR_HARDWARE			1
#define CSERROR_OVERLOAD			2
#define CSERROR_OVERTEMP			4
#define CSERROR_COMMUNICATION		8
#define CSERROR_OVERCURRENT			16
/** current sensor chip codes **/
#define CURRENTSENSOR_VALUEMASK			0x1FFF
#define CURRENTSENSOR_STATUS			15
#define CURRENTSENSOR_PARITY			14
#define CURRENTSENSOR_OCDSTATE			13
#define CURRENTSENSOR_HARDWAREERROR		13
#define CURRENTSENSOR_OVERLOADERROR		12
#define CURRENTSENSOR_OVERTEMPERATURE	11
#define CURRENTSENSOR_COMMERROR			10

#define RX_BUFFER_LENGTH    200

void set_ocd();
void reset_ocd();
bool check_parity(uint16_t x);
void do_measurement();
void storage_event_handler(rbc_mesh_event_t* evt);
bool check_measurement(uint16_t value);
void sensor_init();
uint32_t spi_init();
void overcurrent_init();
void do_storage();
void sensor_event_handler(nrf_timer_event_t event_type, void* p_context);
#endif
#endif
