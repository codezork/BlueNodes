/***********************************************************************************
Copyright (c) 2016 Bluenodes GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are not permitted.
************************************************************************************/

#include "boards.h"
#include "led_config.h"
#include "nrf_timer.h"
#include "nrf_gpio.h"
#include "nrf_drv_gpiote.h"
#include "nrf_drv_ppi.h"
#include "nrf_drv_timer.h"
#include "nrf_drv_common.h"
#include "nrf_delay.h"
#include "nrf_drv_saadc.h"
#include "nrf_drv_spi.h"
#include "app_gpiote.h"
#include "app_util_platform.h"
#include "app_timer.h"
#include "app_error.h"
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include "config.h"
#include "mesh.h"
#include "common.h"
#include "peripheral.h"
#ifdef BATTERY_SERVICE
#include "bas_adc.h"
#endif

#include "SEGGER_RTT.h"

// led test
#define LED_TEST_INTERVAL								APP_TIMER_TICKS(500)
APP_TIMER_DEF(m_led_test_timer_id);
#ifdef TEST_ZERO
#define ZERO_CROSSING_INTERVAL					        APP_TIMER_TICKS(12)
APP_TIMER_DEF(m_zero_test_timer_id);
#endif

#define LOAD_TEST_INTERVAL  					        APP_TIMER_TICKS(500)
APP_TIMER_DEF(m_load_test_timer_id);
// request brightness-timer
APP_TIMER_DEF(m_request_brightness_timer_id);
APP_TIMER_DEF(m_soft_brightness_timer_id);

static const nrf_drv_timer_t                            phaseshift_timer  = NRF_DRV_TIMER_INSTANCE(1);
static const nrf_drv_timer_t                            measurement_timer = NRF_DRV_TIMER_INSTANCE(2);
static const nrf_drv_timer_t                            transfer_counter  = NRF_DRV_TIMER_INSTANCE(3);
static nrf_ppi_channel_t                                m_start_phaseshift_channel;
static uint8_t                                          m_brightness = 0;
static int                                              m_load_test_sequence = 0;
static int								                m_led_test_sequence = 0;
#define BUFFER_SIZE                                     1000                                        /**< 1000 readings per second **/
#define SECOND_BUFFER_SIZE                              60                                          /**< buffer for 60 seconds. */
static float                                            m_sec_buf[SECOND_BUFFER_SIZE];
static float                                            *m_sec_ptr;
#ifdef CURRENT_SENSOR
static const nrf_drv_spi_t 								m_spi = NRF_DRV_SPI_INSTANCE(0);
static uint16_t                                         *m_spi_buf = NULL;
static bool								                m_oc_detected = false;
static mesh_value_t                                     m_overcurrent = {
                                                            .value[0] = 0, \
                                                            .size = 1, \
                                                            .handle = NOTIFY_DEVICE_ERROR_CHARACTERISTIC_HANDLE
                                                        };
#endif
static nrf_saadc_value_t                                m_adc_buf[2][BUFFER_SIZE];     /**< ADC buffer. */
#ifdef AC715
static nrf_saadc_value_t                                *m_adc_ptr;
static nrf_saadc_channel_config_t                       measurement_channel;
#endif
#ifdef BATTERY_SERVICE
static nrf_saadc_channel_config_t                       battery_channel;
static bool                                             is_battery = false;
#endif
#if defined(AC715) && defined(BATTERY_SERVICE)
static uint32_t                                         m_battery_count = 0;
#endif

static uint8_t                                          m_requested_brightness = 0;
static value_notification_evt_handler_t m_evt_handler  = NULL;   /**< event handler for handling adc value notification events. */

/**
* @brief Initialize GPIO pins, for LEDs and debugging
*/
void gpio_init(void) {
#ifdef BOARD_PCA10028
	nrf_gpio_cfg_output(BSP_LED_0);
	nrf_gpio_cfg_output(BSP_LED_1);
	nrf_gpio_cfg_output(BSP_LED_2);
	nrf_gpio_cfg_output(BSP_LED_3);
#endif

#ifdef BOARD_PCA10031
	nrf_gpio_cfg_output(BSP_LED_0);
	nrf_gpio_cfg_output(BSP_LED_1);
	nrf_gpio_cfg_output(BSP_LED_2);
#endif
#ifdef BOARD_BLUENODES
	nrf_gpio_cfg_output(BSP_LED_0);
	nrf_gpio_cfg_output(BSP_LED_1);
#endif
#ifdef BOARD_PCA10001
	nrf_gpio_range_cfg_output(0, 32);
#endif
	led_config(1, 0);
	led_config(2, 0);
#ifndef BOARD_BLUENODES
	led_config(3, 0);
#ifndef PCA10031
	led_config(4, 0);
#endif
#endif
}

// led test timer handler
void led_test_timeout_handler(void * p_context) {
	m_led_test_sequence	++;
	led_config(1, (m_led_test_sequence & 1)==1);
	led_config(2, (m_led_test_sequence & 2)==2);
	if(m_led_test_sequence > 3) {
		led_config(1, 0);
		led_config(2, 0);
		app_timer_stop(m_led_test_timer_id);
	}
}

// load test timer handler
void load_test_timeout_handler(void * p_context) {
	m_load_test_sequence ++;
	set_brightness(m_load_test_sequence*10);
	if(m_load_test_sequence == 10) {
		app_timer_stop(m_load_test_timer_id);
		m_load_test_sequence = 0;
		set_brightness(50);
	}
}

#ifdef TEST_ZERO
void zero_timeout_handler(void * p_context) {
//	NRF_GPIO->OUTCLR = (1 << ZERO_BRIDGE);
//	nrf_delay_us(100);
//	NRF_GPIO->OUTSET = (1 << ZERO_BRIDGE);
    nrf_drv_gpiote_out_task_force(LOAD, 1);
}
#endif

void request_brightness_timeout_handler(void * p_context) {
	app_timer_stop(m_request_brightness_timer_id);
#ifdef VERBOSE
	SEGGER_RTT_printf(0, "answer brightness %d\n", m_brightness);
#endif
	send_mesh_value(ANSWER_BRIGHTNESS_CHARACTERISTIC_HANDLE, &m_brightness, sizeof(uint8_t), false);
}

float calc_watt_hour(float value)
{
    // 1 unit equals +/-5A 10A/(250-108) = 10A/142 = 0.07A
    // P=U*I*cos(phi)
    // in W assumed at = x * 230V*0.07A
    float ampere = value*MAX_AMPERE/(UPPER_LIMIT-LOWER_LIMIT);
    return (VOLTAGE*ampere)/AVG_POWER; ///MINUTES_PER_HOUR;
}

void store_per_second(float value) {
    *m_sec_ptr++ = value;
    if(m_sec_ptr - m_sec_buf == SECOND_BUFFER_SIZE) {
        m_sec_ptr = m_sec_buf;
        value = 0.0;
        while(m_sec_ptr - m_sec_buf < SECOND_BUFFER_SIZE) {
            value += *m_sec_ptr++;
        }
        value /= SECOND_BUFFER_SIZE;
        if(m_evt_handler != NULL) {
            m_evt_handler(value, 1);
        }
        m_sec_ptr = m_sec_buf;
    }
    if(m_evt_handler != NULL) {
        m_evt_handler(value, 0);
    }
}

#ifdef AC715
void store_adc(nrf_saadc_value_t * buffer) {
    // AC712 5A specification with resistor divider 34K8/17K8 = 0.51V - 1.18V
    // 0 A equals 0.85 Volts is with 8bit resolution 0.85V/1.2Vref*2/3*255 = 120
    // 5 A equals 1.18 Volts                                      = 167
    // -5 A equals 0.51 Volts                                     = 72
    // BUFFER_SIZE values were stored
    m_adc_ptr = buffer ;
    float value = 0.0;
    while(m_adc_ptr - buffer < BUFFER_SIZE) {
        value += abs(*m_adc_ptr - ZERO_AMPERE_UNITS);  // value between 0 and 48
        m_adc_ptr ++;
    }
    value /= BUFFER_SIZE;
    store_per_second(value-OFFSET);
}
#endif

void saadc_callback(nrf_drv_saadc_evt_t const * p_event)
{
#ifdef BATTERY_SERVICE
    nrf_saadc_value_t adc_result;
    uint16_t          batt_lvl_in_milli_volts;
    uint8_t           percentage_batt_lvl;
#endif
    if (p_event->type == NRF_DRV_SAADC_EVT_DONE)
    {
        ret_code_t err_code;
        err_code = nrf_drv_saadc_buffer_convert(p_event->data.done.p_buffer, BUFFER_SIZE);
        APP_ERROR_CHECK(err_code);
#ifdef BATTERY_SERVICE
        if(is_battery) {
            adc_result = p_event->data.done.p_buffer[0];
            batt_lvl_in_milli_volts = ADC_RESULT_IN_MILLI_VOLTS(adc_result) + DIODE_FWD_VOLT_DROP_MILLIVOLTS;
            percentage_batt_lvl = battery_level_in_percent(batt_lvl_in_milli_volts);
            battery_level_update(percentage_batt_lvl);
        }
#endif
#if defined(AC715) && defined (BATTERY_SERVICE)
        else
        {
            store_adc(p_event->data.done.p_buffer);
        }
        m_battery_count ++;
        if(m_battery_count%BATTERY_UPDATE_COUNT == 0) {
            is_battery = true;
            APP_ERROR_CHECK(nrf_drv_saadc_channel_uninit(0));
            APP_ERROR_CHECK(nrf_drv_saadc_channel_init(1, &battery_channel));
        } else if(is_battery) {
            is_battery = false;
            APP_ERROR_CHECK(nrf_drv_saadc_channel_uninit(1));
            APP_ERROR_CHECK(nrf_drv_saadc_channel_init(0, &measurement_channel));
        }
#endif
#if defined(AC715) && !defined (BATTERY_SERVICE)
        store_adc(p_event->data.done.p_buffer);
#endif
    }
}

void zerocrossing_event_handler(nrf_drv_gpiote_pin_t pin, nrf_gpiote_polarity_t action) {
	nrf_drv_gpiote_out_task_force(LOAD, 1);
}

void phaseshift_event_handler(nrf_timer_event_t event_type, void* p_context) {
}

#ifdef CURRENT_SENSOR
/**< called when BUFFER_SIZE readings were transferred **/
void transfer_counter_handler(nrf_timer_event_t event_type, void* p_context) {
    uint8_t *ptr = (uint8_t*)m_spi_buf;
    uint16_t error = 0;
    uint16_t measurements = 0;
    float value = 0.0;
    uint16_t measurement = 0;
    while(ptr - (uint8_t*)m_spi_buf < BUFFER_SIZE) {
        // convert big-endian to little-endian
        measurement = ((*ptr)<<8) | (*(ptr+1));
        if(decode_value(measurement))
        {
            value += (float)(abs((measurement & CURRENTSENSOR_VALUEMASK) - 4096));
            measurements ++;
        }
        else
        {
            error ++;
        }
        ptr ++;
    }
    // arithmetic middle
    if(measurements > 0) {
        value /= measurements;
        store_per_second(value);
    }
}

bool check_parity(uint16_t x)
{
    // parity bit is set in a way that the sum of all bits in the value word is odd.
    x ^= x >> 8;
    x ^= x >> 4;
    x ^= x >> 2;
    x ^= x >> 1;
    return true;
    return x & 1;
}

bool decode_value(uint16_t value) {
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
#endif

/** @brief Function for timer initialization, which will be started by zero-crossing using PPI.
*/
void high_resolution_timer_init(void) {
	nrf_drv_timer_config_t timer_config;
	timer_config.frequency = NRF_TIMER_FREQ_1MHz;
	timer_config.bit_width = NRF_TIMER_BIT_WIDTH_16;
	timer_config.interrupt_priority = APP_IRQ_PRIORITY_LOW;
	timer_config.mode = NRF_TIMER_MODE_TIMER;
	timer_config.p_context = NULL;
	APP_ERROR_CHECK(nrf_drv_timer_init(&phaseshift_timer, &timer_config, phaseshift_event_handler));
#if defined(AC715) || defined(CURRENT_SENSOR)
	APP_ERROR_CHECK(nrf_drv_timer_init(&measurement_timer, &timer_config, phaseshift_event_handler));
	// 1000 cycles at 1MHz makes 0.001 sec - makes 10 sensor readings during one half wave at 50Hz mains AC frequency
    nrf_drv_timer_extended_compare(&measurement_timer, NRF_TIMER_CC_CHANNEL0, 1000, NRF_TIMER_SHORT_COMPARE0_CLEAR_MASK, false);
    nrf_drv_timer_enable(&measurement_timer);
#else
	// 10000 cycles at 1MHz makes 0.01 sec - the duration of one half wave at 50Hz mains AC frequency
    nrf_drv_timer_extended_compare(&phaseshift_timer, NRF_TIMER_CC_CHANNEL0, 10000, NRF_TIMER_SHORT_COMPARE0_CLEAR_MASK, false);
#endif
}

/** @brief Function for initializing the PPI peripheral.
*/
uint32_t peripheral_init(value_notification_evt_handler_t evt_handler) {
	uint32_t err_code = NRF_SUCCESS;

	m_evt_handler = evt_handler;

	nrf_ppi_channel_t leading_edge_channel;
	nrf_ppi_channel_t trailing_edge_channel;
	nrf_ppi_channel_t stop_phaseshift_channel;
	uint32_t zerocrossing_addr;
	uint32_t phaseshift_start_addr;
	uint32_t phaseshift_end_addr;
	uint32_t phaseshift_stop_addr;
	uint32_t load_addr;
	uint32_t reading_addr;
#ifdef CURRENT_SENSOR
    nrf_ppi_channel_t spi_sample_channel;
    nrf_ppi_channel_t transfer_counter_channel;
	uint32_t spi_start_addr;
    uint32_t counter_increment_addr;
    uint32_t transfer_complete_addr;
    nrf_drv_timer_config_t counter_config = NRF_DRV_TIMER_DEFAULT_CONFIG;
    counter_config.mode = NRF_TIMER_MODE_COUNTER;
    APP_ERROR_CHECK(nrf_drv_timer_init(&transfer_counter, &counter_config, transfer_counter_handler));
    nrf_drv_timer_extended_compare(&transfer_counter, NRF_TIMER_CC_CHANNEL0, BUFFER_SIZE, NRF_TIMER_SHORT_COMPARE0_CLEAR_MASK, true);
#endif
#if defined(AC715) || defined(BATTERY_SERVICE)
	nrf_ppi_channel_t adc_sample_channel;
	uint32_t adc_start_addr;
#endif

#ifdef VERBOSE
	SEGGER_RTT_printf(0, "peripheral init\n");
#endif
	// init the phase-shift and pulse-length timer
	high_resolution_timer_init();
	APP_ERROR_CHECK(nrf_drv_ppi_init());

	if (!nrf_drv_gpiote_is_init()) {
		err_code = nrf_drv_gpiote_init();
		if(err_code != NRF_SUCCESS) {
			return err_code;
		}
	}
#ifdef VERBOSE
	SEGGER_RTT_printf(0, "ppi channels\n");
#endif
	// create the zero-crossing event sense
	nrf_drv_gpiote_in_config_t zero_config = GPIOTE_CONFIG_IN_SENSE_LOTOHI(true);
	zero_config.pull = NRF_GPIO_PIN_PULLUP;
	APP_ERROR_CHECK(nrf_drv_gpiote_in_init(ZERO, &zero_config, zerocrossing_event_handler));

	// create the load control task
	nrf_drv_gpiote_out_config_t load_config = GPIOTE_CONFIG_OUT_TASK_TOGGLE(true);
	APP_ERROR_CHECK(nrf_drv_gpiote_out_init(LOAD, &load_config));
#ifdef CURRENT_SENSOR
    // init over current signal
    nrf_drv_gpiote_in_config_t ocd_config = GPIOTE_CONFIG_IN_SENSE_LOTOHI(true);
    ocd_config.pull = NRF_GPIO_PIN_PULLUP;
    APP_ERROR_CHECK(nrf_drv_gpiote_in_init(OCD_PIN, &ocd_config, ocd_event_handler));
    nrf_drv_gpiote_in_event_enable(OCD_PIN, true);
    // init spim
    nrf_drv_spi_config_t spi_config;
    spi_config.frequency = NRF_DRV_SPI_FREQ_4M;
    spi_config.mode      = NRF_DRV_SPI_MODE_1; //< SCK active high, sample on trailing edge of clock.
    spi_config.bit_order = NRF_DRV_SPI_BIT_ORDER_MSB_FIRST;
    spi_config.orc		 = 0;
    spi_config.mosi_pin  = NRF_DRV_SPI_PIN_NOT_USED;
    spi_config.miso_pin  = SPIM0_MISO_PIN;
    spi_config.sck_pin   = SPIM0_SCK_PIN;
    spi_config.ss_pin    = SPIM0_SS_PIN;
    spi_config.irq_priority = APP_IRQ_PRIORITY_LOW;
    APP_ERROR_CHECK(nrf_drv_spi_init(&m_spi, &spi_config, NULL, NULL));
    m_spi_buf = malloc(sizeof(uint16_t)*BUFFER_SIZE);
    memset(m_spi_buf, 0, sizeof(uint16_t)*BUFFER_SIZE);
    nrf_drv_spi_xfer_desc_t xfer = NRF_DRV_SPI_XFER_RX(m_spi_buf, sizeof(uint16_t));
    uint32_t flags = NRF_DRV_SPI_FLAG_HOLD_XFER           |
                     NRF_DRV_SPI_FLAG_RX_POSTINC          |
                     NRF_DRV_SPI_FLAG_NO_XFER_EVT_HANDLER |
                     NRF_DRV_SPI_FLAG_REPEATED_XFER;
    err_code=nrf_drv_spi_xfer(&m_spi, &xfer, flags);
    APP_ERROR_CHECK(err_code);
#endif
#ifdef AC715
	nrf_drv_gpiote_out_config_t ind_config = GPIOTE_CONFIG_OUT_TASK_TOGGLE(false);
	APP_ERROR_CHECK(nrf_drv_gpiote_out_init(ADC_SAMPLE, &ind_config));
    //Initialize ADC
    measurement_channel.resistor_p = NRF_SAADC_RESISTOR_DISABLED;
    measurement_channel.resistor_n = NRF_SAADC_RESISTOR_DISABLED;
    measurement_channel.gain       = NRF_SAADC_GAIN1_6;
    measurement_channel.reference  = NRF_SAADC_REFERENCE_INTERNAL;
    measurement_channel.acq_time   = NRF_SAADC_ACQTIME_20US;
    measurement_channel.mode       = NRF_SAADC_MODE_SINGLE_ENDED;
    measurement_channel.burst      = NRF_SAADC_BURST_DISABLED;
    measurement_channel.pin_p      = (nrf_saadc_input_t)(NRF_SAADC_INPUT_AIN2);
    measurement_channel.pin_n      = NRF_SAADC_INPUT_DISABLED;
#endif
#ifdef BATTERY_SERVICE
    battery_channel.resistor_p = NRF_SAADC_RESISTOR_DISABLED;
    battery_channel.resistor_n = NRF_SAADC_RESISTOR_DISABLED;
    battery_channel.gain       = NRF_SAADC_GAIN1_6;
    battery_channel.reference  = NRF_SAADC_REFERENCE_INTERNAL;
    battery_channel.acq_time   = NRF_SAADC_ACQTIME_20US;
    battery_channel.mode       = NRF_SAADC_MODE_SINGLE_ENDED;
    battery_channel.burst      = NRF_SAADC_BURST_DISABLED;
    battery_channel.pin_p      = (nrf_saadc_input_t)(NRF_SAADC_INPUT_VDD);
    battery_channel.pin_n      = NRF_SAADC_INPUT_DISABLED;
#endif
#if defined(AC715) || defined(BATTERY_SERVICE)
    APP_ERROR_CHECK(nrf_drv_saadc_init(NULL, saadc_callback));
	adc_start_addr          = nrf_drv_saadc_sample_task_get();
#endif
#if !defined(AC715) && defined(BATTERY_SERVICE)
    APP_ERROR_CHECK(nrf_drv_saadc_channel_init(0, &battery_channel));
    is_battery = true;
#endif
#if defined(AC715)
    APP_ERROR_CHECK(nrf_drv_saadc_channel_init(0, &measurement_channel));
#endif
	// init channels
	APP_ERROR_CHECK(nrf_drv_ppi_channel_alloc(&m_start_phaseshift_channel));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_alloc(&stop_phaseshift_channel));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_alloc(&leading_edge_channel));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_alloc(&trailing_edge_channel));
#ifdef CURRENT_SENSOR
	APP_ERROR_CHECK(nrf_drv_ppi_channel_alloc(&spi_sample_channel));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_alloc(&transfer_counter_channel));
#endif
#if defined(AC715) || defined(BATTERY_SERVICE)
	APP_ERROR_CHECK(nrf_drv_ppi_channel_alloc(&adc_sample_channel));
#endif
	// init addresses
	zerocrossing_addr = nrf_drv_gpiote_in_event_addr_get(ZERO);
	load_addr = nrf_drv_gpiote_out_task_addr_get(LOAD);

	phaseshift_start_addr   = nrf_drv_timer_task_address_get(&phaseshift_timer, NRF_TIMER_TASK_START);
	phaseshift_stop_addr    = nrf_drv_timer_event_address_get(&phaseshift_timer, NRF_TIMER_TASK_STOP);
#ifdef CURRENT_SENSOR
	reading_addr            = nrf_drv_timer_event_address_get(&measurement_timer, NRF_TIMER_EVENT_COMPARE0);
	spi_start_addr          = nrf_drv_spi_start_task_get(&m_spi);
	transfer_complete_addr  = nrf_drv_spi_end_event_get(&m_spi);
    counter_increment_addr  = nrf_drv_timer_task_address_get(&transfer_counter, NRF_TIMER_TASK_COUNT);
#endif
#ifdef AC715
	reading_addr            = nrf_drv_timer_event_address_get(&measurement_timer, NRF_TIMER_EVENT_COMPARE0);
#endif
	phaseshift_end_addr     = nrf_drv_timer_event_address_get(&phaseshift_timer, NRF_TIMER_EVENT_COMPARE0);

	// enable i/o
	nrf_drv_gpiote_in_event_enable(ZERO, true);
	nrf_drv_gpiote_out_task_enable(LOAD);
    // create buffer for measurements
    APP_ERROR_CHECK(nrf_drv_saadc_buffer_convert(m_adc_buf[0], BUFFER_SIZE));
    APP_ERROR_CHECK(nrf_drv_saadc_buffer_convert(m_adc_buf[1], BUFFER_SIZE));
	memset(m_sec_buf, 0, SECOND_BUFFER_SIZE);
	m_sec_ptr = m_sec_buf;
#ifdef VERBOSE
	SEGGER_RTT_printf(0, "ppi channels assign\n");
#endif
	APP_ERROR_CHECK(nrf_drv_ppi_channel_assign(m_start_phaseshift_channel,
					zerocrossing_addr,
					phaseshift_start_addr));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_assign(leading_edge_channel,
					zerocrossing_addr,
					load_addr));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_assign(trailing_edge_channel,
					phaseshift_end_addr,
					load_addr));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_assign(stop_phaseshift_channel,
					phaseshift_end_addr,
					phaseshift_stop_addr));
#ifdef CURRENT_SENSOR
	APP_ERROR_CHECK(nrf_drv_ppi_channel_assign(spi_sample_channel,
					reading_addr,
					spi_start_addr));
    APP_ERROR_CHECK(nrf_drv_ppi_channel_assign(transfer_counter_channel,
                    transfer_complete_addr,
                    counter_increment_addr));
    nrf_drv_timer_enable(&transfer_counter);
#endif
#if defined(AC715) || defined(BATTERY_SERVICE)
	APP_ERROR_CHECK(nrf_drv_ppi_channel_assign(adc_sample_channel,
					reading_addr,
					adc_start_addr));
#endif

#ifdef VERBOSE
	SEGGER_RTT_printf(0, "ppi channels enable\n");
#endif
	// enable channels
	APP_ERROR_CHECK(nrf_drv_ppi_channel_enable(leading_edge_channel));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_enable(trailing_edge_channel));
	APP_ERROR_CHECK(nrf_drv_ppi_channel_enable(stop_phaseshift_channel));
#ifdef CURRENT_SENSOR
	APP_ERROR_CHECK(nrf_drv_ppi_channel_enable(spi_sample_channel));
    APP_ERROR_CHECK(nrf_drv_ppi_channel_enable(transfer_counter_channel));
#endif
#if defined(AC715) || defined(BATTERY_SERVICE)
	APP_ERROR_CHECK(nrf_drv_ppi_channel_enable(adc_sample_channel));
#endif
	// create request brightness timer
	APP_ERROR_CHECK(app_timer_create(&m_request_brightness_timer_id,
									 APP_TIMER_MODE_SINGLE_SHOT,
									 request_brightness_timeout_handler));

	APP_ERROR_CHECK(app_timer_create(&m_soft_brightness_timer_id,
									 APP_TIMER_MODE_REPEATED,
									 soft_brightness_timeout_handler));

#ifdef TEST_ZERO
	// create current storage values
	APP_ERROR_CHECK(app_timer_create(&m_zero_test_timer_id,
									 APP_TIMER_MODE_REPEATED,
									 zero_timeout_handler));
	app_timer_start(m_zero_test_timer_id, ZERO_CROSSING_INTERVAL, NULL);
	SEGGER_RTT_WriteString(0, "zero crossing test started\n");
#endif

#ifdef VERBOSE
	SEGGER_RTT_printf(0, "peripheral init done\n");
#endif
    return NRF_SUCCESS;
}

void request_brightness() {
	app_timer_stop(m_request_brightness_timer_id);
	app_timer_start(m_request_brightness_timer_id, NOTIFICATION_TIMEOUT, NULL);
}

void reset_brightness() {
	set_brightness(m_brightness);
}

void set_brightness_soft(uint8_t brightness) {
    m_requested_brightness = brightness;
	app_timer_stop(m_soft_brightness_timer_id);
	app_timer_start(m_soft_brightness_timer_id, SOFT_BRIGHTNESS, NULL);
}

void soft_brightness_timeout_handler(void * p_context) {
    if(m_requested_brightness > m_brightness) {
        set_brightness(m_brightness+1);
    } else if(m_requested_brightness < m_brightness) {
        set_brightness(m_brightness-1);
    } else {
        app_timer_stop(m_soft_brightness_timer_id);
    }
}

void set_brightness(uint8_t brightness) {
	m_brightness = brightness;
#ifdef VERBOSE
	SEGGER_RTT_printf(0, "brightness: %d\n", brightness);
#endif
	if(brightness > 5 && brightness < 95) {
		// enable zero-crossing event
		nrf_drv_gpiote_out_task_enable(LOAD);
		nrf_drv_ppi_channel_enable(m_start_phaseshift_channel);
		uint16_t counter;
		counter = 10000 - 80*(100-brightness);
		nrf_drv_timer_extended_compare(&phaseshift_timer, NRF_TIMER_CC_CHANNEL0, counter, NRF_TIMER_SHORT_COMPARE0_CLEAR_MASK, true);
	} else {
		// disable zero-crossing event
		nrf_drv_ppi_channel_disable(m_start_phaseshift_channel);
		nrf_drv_gpiote_out_task_disable(LOAD);
		if(brightness <= 5) {
			nrf_drv_gpiote_out_clear(LOAD);
		} else {
			nrf_drv_gpiote_out_set(LOAD);
		}
	}
}

void led_test(void) {
	ret_code_t err_code;
	err_code = app_timer_create(&m_led_test_timer_id,
								APP_TIMER_MODE_REPEATED,
								led_test_timeout_handler);
	APP_ERROR_CHECK(err_code);
	err_code = app_timer_start(m_led_test_timer_id, LED_TEST_INTERVAL, NULL);
	APP_ERROR_CHECK(err_code);
}

void load_test(void) {
	ret_code_t err_code;
	err_code = app_timer_create(&m_load_test_timer_id,
								APP_TIMER_MODE_REPEATED,
								load_test_timeout_handler);
	APP_ERROR_CHECK(err_code);
	err_code = app_timer_start(m_load_test_timer_id, LOAD_TEST_INTERVAL, NULL);
	APP_ERROR_CHECK(err_code);
}
