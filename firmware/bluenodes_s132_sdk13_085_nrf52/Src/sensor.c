/***********************************************************************************
Copyright (c) Bluenodes GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are not permitted.
************************************************************************************/

#include "nrf_error.h"
#include "nrf_timer.h"
#include "nrf_drv_timer.h"
#include "nrf_drv_spi.h"
#include "nordic_common.h"
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include "rbc_mesh.h"
#include "app_util_platform.h"
#include "app_error.h"
#include "app_scheduler.h"
#include "app_gpiote.h"
#include "app_timer_appsh.h"
#include "sensor.h"
#include "peripheral.h"
#include "current_cube.h"
#include "mesh.h"
#include "common.h"
#include "led_config.h"
#include "boards.h"

#include "SEGGER_RTT.h"

#ifdef CURRENTSENSOR

#ifndef OCD
#define OCD  10
#endif

#define ZERO_BRIDGE 18

// spi master
#define SPI_INSTANCE                                    0
static const nrf_drv_spi_t 								m_spi = NRF_DRV_SPI_INSTANCE(SPI_INSTANCE);
static const nrf_drv_timer_t                            measurement_timer = NRF_DRV_TIMER_INSTANCE(2);

#define CURRENT_MEASUREMENT_INTERVAL					APP_TIMER_TICKS(500, APP_TIMER_PRESCALER)
APP_TIMER_DEF(m_measurement_timer_id);

static volatile bool spi_xfer_done = true;              /**< Flag used to indicate that SPI instance completed the transfer. */
static uint8_t *m_rx_buf;                               /**< RX buffer. */
static uint8_t *m_rx_ptr;

static bool								m_oc_detected = false;
static volatile bool                    m_radio_active = false;
static volatile float                   m_value = 0;
static volatile bool                    spi_transfer_done = true;
static volatile bool                    send_message_done = true;

static int m_counter = 0;

static mesh_value_t                     m_overcurrent = {
    .value[0] = 0, \
    .size = 1, \
    .handle = NOTIFY_DEVICE_ERROR_CHARACTERISTIC_HANDLE};

static mesh_value_t                     m_message = {
    .value[0] = 0, \
    .value[1] = 0, \
    .value[2] = 0, \
    .value[3] = 0, \
    .size = 4, \
    .handle = MESSAGE_CHARACTERISTIC_HANDLE};

void set_ocd()
{
    set_brightness(0);
    m_oc_detected = true;
}

void reset_ocd()
{
    m_oc_detected = false;
    reset_brightness();
}

void ocd_event_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action)
{
    // over current was detected
    set_ocd();
    m_overcurrent.value[0] = CSERROR_OVERCURRENT;
    m_overcurrent.size = 1;
    m_overcurrent.handle = NOTIFY_DEVICE_ERROR_CHARACTERISTIC_HANDLE;
    notify_error(&m_overcurrent);
}

void overcurrent_init()
{
    // init the overcurrent interrupt
    nrf_drv_gpiote_in_config_t ocd_config = GPIOTE_CONFIG_IN_SENSE_LOTOHI(true);
    ocd_config.pull = NRF_GPIO_PIN_PULLUP;
    APP_ERROR_CHECK(nrf_drv_gpiote_in_init(OCD, &ocd_config, ocd_event_handler));
    nrf_drv_gpiote_in_event_enable(OCD, true);
}

void sensor_timeout_handler(void * p_context)
{
    if(spi_transfer_done) {
        send_message_done = false;
        send_mesh_value(m_message.handle, &m_message.value[0], sizeof(float), true);
        send_message_done = true;
    }
}

void trigger_event_handler(nrf_timer_event_t event_type, void* p_context)
{
    switch(event_type)
    {
        case NRF_TIMER_EVENT_COMPARE0:
            if(send_message_done) {
                do_measurement();
            }
            break;
        default:
            //Do nothing.
            break;
    }
}

void sensor_init()
{
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "sensor init\n");
#endif
    uint32_t err;
    err = spi_init();
    if(err != NRF_SUCCESS) {
   		APP_ERROR_CHECK(err);
        return;
    }
    // create current sensor timer
    APP_ERROR_CHECK(app_timer_create(&m_measurement_timer_id,
                                APP_TIMER_MODE_REPEATED,
                                measurement_timeout_handler));
    // create current storage values
    APP_ERROR_CHECK(app_timer_create(&m_request_storage_timer_id,
                                APP_TIMER_MODE_REPEATED,
                                request_storage_timeout_handler));
	nrf_drv_timer_config_t timer_config;
	timer_config.frequency = NRF_TIMER_FREQ_1MHz;
	timer_config.bit_width = NRF_TIMER_BIT_WIDTH_16;
	timer_config.interrupt_priority = APP_IRQ_PRIORITY_LOW;
	timer_config.mode = NRF_TIMER_MODE_TIMER;
	timer_config.p_context = NULL;
	APP_ERROR_CHECK(nrf_drv_timer_init(&measurement_timer, &timer_config, trigger_event_handler));
    nrf_drv_timer_extended_compare(&measurement_timer, NRF_TIMER_CC_CHANNEL0, 500, NRF_TIMER_SHORT_COMPARE0_CLEAR_MASK, true);

    APP_ERROR_CHECK(cube_init(&must_store));
    APP_ERROR_CHECK(app_timer_start(m_measurement_timer_id, CURRENT_MEASUREMENT_INTERVAL, NULL));
    nrf_drv_timer_enable(&measurement_timer);
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "sensor init done\n");
#endif
}

void do_measurement() {
    m_counter ++;
    if(m_counter % 100 == 0) {
        led_toggle(2);
    }
    spi_transfer_done = false;
    nrf_drv_spi_transfer(&m_spi, NULL, 0, (uint8_t*)m_rx_ptr, 2);
}

void spi_event_handler(nrf_drv_spi_evt_t * p_event)
{
    uint16_t value;
    float    power = 0;
    uint16_t error = 0;
    uint16_t measurements = 0;

    spi_transfer_done = true;
    m_rx_ptr += 2;
    if(m_rx_ptr - m_rx_buf > RX_BUFFER_LENGTH)
    {
        m_rx_ptr = m_rx_buf;
        power = 0;
        while(m_rx_ptr - m_rx_buf <= RX_BUFFER_LENGTH)
        {
            // convert big-endian to little-endian
            value = ((*m_rx_ptr)<<8) | (*(m_rx_ptr+1));
            if(decode_value(value))
            {
                power += (float)(abs((value & CURRENTSENSOR_VALUEMASK) - 4096));
                measurements ++;
            }
            else
            {
                error ++;
            }
            m_rx_ptr += 2;
        }
        // arithmetic middle
        if(measurements > 0) {
            power /= measurements;
        }
        power *= 230/160;

#ifdef TEST_ZERO
        power = 7.4;
#endif
//		cube_set_value(power);
        memcpy((uint8_t*)&m_value, (uint8_t*)&power, sizeof(float));
        memset((uint8_t*)m_rx_buf, 0, RX_BUFFER_LENGTH);
        m_rx_ptr = m_rx_buf;

        memcpy((uint8_t*)m_message.value, (uint8_t*)&m_value, sizeof(float));
    }
}

uint32_t spi_init()
{
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "spi init\n");
#endif
    uint32_t err;
    nrf_drv_spi_config_t config;
    config.frequency = NRF_DRV_SPI_FREQ_4M;
    config.mode      = NRF_DRV_SPI_MODE_1; //< SCK active high, sample on trailing edge of clock.
    config.bit_order = NRF_DRV_SPI_BIT_ORDER_MSB_FIRST;
    config.orc		 = 0;
    config.mosi_pin  = NRF_DRV_SPI_PIN_NOT_USED;
    config.miso_pin  = SPIM0_MISO_PIN;
    config.sck_pin   = SPIM0_SCK_PIN;
    config.ss_pin    = SPIM0_SS_PIN;
    config.irq_priority = APP_IRQ_PRIORITY_LOW;

	m_rx_buf = malloc((RX_BUFFER_LENGTH+1)*sizeof(uint8_t));
	if(m_rx_buf == NULL) {
		return NRF_ERROR_NO_MEM;
	}
	memset(m_rx_buf, 0, RX_BUFFER_LENGTH);
	m_rx_ptr = m_rx_buf;
    err = nrf_drv_spi_init(&m_spi, &config, (nrf_drv_spi_handler_t)spi_event_handler);
    if(err != NRF_SUCCESS) {
        return err;
    }
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "spi init done\n");
#endif
    return NRF_SUCCESS;

}

bool check_parity(uint16_t x)
{
    // parity bit is set in a way that the sum of all bits in the value word is odd.
    x ^= x >> 8;
    x ^= x >> 4;
    x ^= x >> 2;
    x ^= x >> 1;
    return x & 1;
}

bool decode_value(uint16_t value)
{
    m_overcurrent.value[0] =  0;
    if(check_parity(value))
    {
        // dispatch message
        if(value & (1<<CURRENTSENSOR_STATUS))
        {
            // status
            if (value & (1<<CURRENTSENSOR_HARDWAREERROR))
            {
                m_overcurrent.value[0] |= CSERROR_HARDWARE;
            }
            else if (value & (1<<CURRENTSENSOR_OVERLOADERROR))
            {
                m_overcurrent.value[0] |= CSERROR_OVERLOAD;
            }
            else if (value & (1<<CURRENTSENSOR_OVERTEMPERATURE))
            {
                m_overcurrent.value[0] |= CSERROR_OVERTEMP;
            }
            else if (value & (1<<CURRENTSENSOR_COMMERROR))
            {
                m_overcurrent.value[0] |= CSERROR_COMMUNICATION;
            }
            if(m_overcurrent.value[0] != 0)
            {
                // notify user
                notify_error(&m_overcurrent);
                return false;
            }
        }
        else
        {
            if (value & (1<<CURRENTSENSOR_OCDSTATE))
            {
                if(!m_oc_detected)
                {
                    set_ocd();
                    m_overcurrent.value[0] |= CSERROR_OVERCURRENT;
                    return false;
                }
            }
            else
            {
                // can we reset the overcurrent state?
                if(!(value & (1<<CURRENTSENSOR_OCDSTATE)) && m_oc_detected)
                {
                    reset_ocd();
                }
            }
        }
    }
    else
    {
        return false;
    }
    return true;
}
#endif
