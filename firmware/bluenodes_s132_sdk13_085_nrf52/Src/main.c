/***********************************************************************************
Copyright (c) Bluenodes GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are not permitted.
************************************************************************************/

#include "rbc_mesh.h"
#include "nrf_adv_conn.h"
#include "nrf_soc.h"
#include "softdevice_handler.h"
#include "nrf_drv_rng.h"
#include <string.h>
#include "app_button.h"
#include "app_timer.h"
#include "bas_adc.h"
#include "fstorage.h"
#include "fstorage_internal_defs.h"
#include "led_config.h"
#include "mesh.h"
#include "config.h"
#include "common.h"
#include "peripheral.h"
#include "current_cube.h"
#ifdef MESH_DFU
#include "dfu_app.h"
#endif

#include "SEGGER_RTT.h"

/** rtc timers **/
// request addresses timeout
#define REQUEST_DEVICEADDRESSES_TIMEOUT					APP_TIMER_TICKS(10)
APP_TIMER_DEF(m_request_deviceaddress_timer_id);
#define TEMPERATURE_TIMEOUT					            APP_TIMER_TICKS(1000)
APP_TIMER_DEF(m_temperature_timer_id);
// notify error timeout
APP_TIMER_DEF(m_notify_error_timer_id);

// message to host service timeout
APP_TIMER_DEF(m_message_timer_id);

/** global variables **/
static mesh_value_t                     m_message;
static volatile bool                    m_radio_active = false;
static volatile bool                    m_connected = false;
static volatile bool                    m_memory_access_in_progress = false;
// global static device adress
static ble_gap_addr_t 					m_my_addr;

/**
* @brief General error handler.
*/
static void error_loop(void) {
	while (true) {
		__WFE();
	}
}

/**@brief Callback function for asserts in the SoftDevice.
 *
 * @details This function will be called in case of an assert in the SoftDevice.
 *
 * @warning This handler is an example only and does not fit a final product. You need to analyze
 *          how your product is supposed to react in case of Assert.
 * @warning On assert from the SoftDevice, the system can only recover on reset.
 *
 * @param[in] line_num   Line number of the failing ASSERT call.
 * @param[in] file_name  File name of the failing ASSERT call.
 */
void assert_nrf_callback(uint16_t line_num, const uint8_t * p_file_name)
{
    app_error_handler(DEAD_BEEF, line_num, p_file_name);
}

void app_error_fault_handler(uint32_t id, uint32_t pc, uint32_t info) {
#ifndef DEBUG
    NVIC_SystemReset();
#else
    SEGGER_RTT_printf(0, "app_error_print():\r\n");
    SEGGER_RTT_printf(0, "Fault identifier:  0x%X\r\n", id);
    SEGGER_RTT_printf(0, "Program counter:   0x%X\r\n", pc);
    SEGGER_RTT_printf(0, "Fault information: 0x%X\r\n", info);

#ifndef DEBUG
    if(info == 0) {
        SEGGER_RTT_printf(0, "reset due to sd assert\r\n");
        NVIC_SystemReset();
    }
#endif
    switch (id)
    {
        case NRF_FAULT_ID_SDK_ASSERT:
            SEGGER_RTT_printf(0, "Line Number: %u\r\n", ((assert_info_t *)(info))->line_num);
            SEGGER_RTT_printf(0, "File Name:   %s\r\n", ((assert_info_t *)(info))->p_file_name);
            break;

        case NRF_FAULT_ID_SDK_ERROR:
            SEGGER_RTT_printf(0, "Line Number: %u\r\n",   ((error_info_t *)(info))->line_num);
            SEGGER_RTT_printf(0, "File Name:   %s\r\n",   ((error_info_t *)(info))->p_file_name);
            SEGGER_RTT_printf(0, "Error Code:  0x%X\r\n", ((error_info_t *)(info))->err_code);
            break;
    }
#endif
}

/**
* @brief Softdevice crash handler, never returns
*
* @param[in] pc Program counter at which the assert failed
* @param[in] line_num Line where the error check failed
* @param[in] p_file_name File where the error check failed
*/
void sd_assert_handler(uint32_t pc, uint16_t line_num, const uint8_t* p_file_name) {
    SEGGER_RTT_printf(0, "sd_error_print():\r\n");
    SEGGER_RTT_printf(0, "Program counter:   0x%X\r\n", pc);
    SEGGER_RTT_printf(0, "Line Number: %u\r\n", line_num);
    SEGGER_RTT_printf(0, "File Name:   %s\r\n", p_file_name);
	error_loop();
}

void HardFault_Handler(void) {
	error_loop();
}

void ble_radio_notification_evt_handler(bool radio_active) {
    m_radio_active = radio_active;
}

void ble_connect_event_handler(bool connected) {
    m_connected = connected;
    led_config(1,connected);
    if(connected) {
        SEGGER_RTT_printf(0, "CONNECTED\n");
    } else {
        SEGGER_RTT_printf(0, "DISCONNECTED\n");
    }
}

void adc_value_notification_evt_handler(float value, bool isMinute) {
    app_timer_stop(m_message_timer_id);
    float pwr = calc_watt_hour(value);
    pwr = value;
#ifdef SHOW_POWER
    if(m_connected) {
        memcpy((uint8_t*)m_message.value, (uint8_t*)&pwr, sizeof(float));
        m_message.size = sizeof(float);
        m_message.handle = MESSAGE_CHARACTERISTIC_HANDLE;
        app_timer_start(m_message_timer_id, NOTIFICATION_TIMEOUT, &m_message);
    }
#endif
    if(isMinute) {
        cube_set_power(pwr);
    }
}

/**
* @brief Softdevice event handler
*/
void sd_ble_evt_handler(ble_evt_t* p_ble_evt)
{
    rbc_mesh_ble_evt_handler(p_ble_evt);
    nrf_adv_conn_evt_handler(p_ble_evt);
#ifdef BATTERY_SERVICE
    bas_adc_on_ble_evt(p_ble_evt);
#endif
}

/**@brief Function for handling the Application's system events.
 *
 * @param[in]   sys_evt   system event.
 */
static void on_sys_evt(uint32_t sys_evt)
{
    switch (sys_evt)
    {
        case NRF_EVT_FLASH_OPERATION_SUCCESS:
        /* fall through */
        case NRF_EVT_FLASH_OPERATION_ERROR:

            if (m_memory_access_in_progress)
            {
                m_memory_access_in_progress = false;
            }
            break; // NRF_EVT_FLASH_OPERATION_SUCCESS and NRF_EVT_FLASH_OPERATION_ERROR
        default:
            // No implementation needed.
            break;
    }
}

/**@brief Function for dispatching a system event to interested modules.
 *
 * @details This function is called from the System event interrupt handler after a system
 *          event has been received.
 *
 * @param[in]   sys_evt   System stack event.
 */
static void sys_evt_dispatch(uint32_t sys_evt)
{
    rbc_mesh_sd_evt_handler(sys_evt);
    fs_sys_event_handler(sys_evt);
    on_sys_evt(sys_evt);
}

void print_data(char *type, uint8_t *address, uint8_t len)
{
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "%s ", type);
    for(char i=0; i<len; i++)
        SEGGER_RTT_printf(0, "%02x", *(address+i));
    SEGGER_RTT_printf(0, "\n");
#endif
}

void send_mesh_value(uint8_t characteristic, uint8_t *in, uint8_t size, bool with_address) {
	uint32_t err_code;
	uint8_t tx_buffer[RBC_MESH_VALUE_MAX_LEN];
	memset(tx_buffer, 0, RBC_MESH_VALUE_MAX_LEN);
	uint8_t *ptr = tx_buffer;
	uint8_t max_size = RBC_MESH_VALUE_MAX_LEN;
	if(with_address) {
		memcpy(ptr, (uint8_t*)m_my_addr.addr, BLE_GAP_ADDR_LEN);
		ptr += BLE_GAP_ADDR_LEN;
		max_size -= BLE_GAP_ADDR_LEN;
	}
	if(max_size < size) {
		size = max_size;
	}
	memcpy(ptr, in, size);
	uint8_t length = with_address?BLE_GAP_ADDR_LEN+size:size;
	err_code = rbc_mesh_value_set(characteristic, tx_buffer, length);
	if (err_code != NRF_SUCCESS &&
			err_code != BLE_ERROR_INVALID_CONN_HANDLE &&
			err_code != NRF_ERROR_INVALID_STATE) {
		APP_ERROR_CHECK(err_code);
	}
}

void request_deviceaddresses_timeout_handler(void * p_context) {
	uint32_t err_code;

	app_timer_stop(m_request_deviceaddress_timer_id);
#ifdef VERBOSE
	print_data("answer address", m_my_addr.addr, BLE_GAP_ADDR_LEN);
#endif
	err_code = rbc_mesh_value_set(ANSWER_DEVICEADDRESS_CHARACTERISTIC_HANDLE, (void*)m_my_addr.addr, BLE_GAP_ADDR_LEN);
	if (err_code != NRF_SUCCESS &&
			err_code != BLE_ERROR_INVALID_CONN_HANDLE &&
			err_code != NRF_ERROR_INVALID_STATE) {
		APP_ERROR_CHECK(err_code);
	}
}

void message_timeout_handler(void * p_context) {
	mesh_value_t *val = p_context;
	send_mesh_value(val->handle, &val->value[0], val->size, true);
}

void message(mesh_value_t *message) {
	app_timer_stop(m_message_timer_id);
	memcpy(&m_message, message, sizeof(mesh_value_t));
	app_timer_start(m_message_timer_id, NOTIFICATION_TIMEOUT, &m_message);
}

void notify_error_timeout_handler(void * p_context)
{
	mesh_value_t *val = p_context;
	send_mesh_value(val->handle, &val->value[0], val->size, true);
}

void notify_error(mesh_value_t *message) {
	app_timer_stop(m_notify_error_timer_id);
	memcpy(&m_message, message, sizeof(mesh_value_t));
	app_timer_start(m_notify_error_timer_id, NOTIFICATION_TIMEOUT, &m_message);
}

void temperature_timeout_handler(void * p_context) {
#ifdef SHOW_TEMPERATURE
    int32_t temp;
    if(m_connected && sd_temp_get(&temp) == NRF_SUCCESS) {
        float value = ((float)temp/4);
        send_mesh_value(MESSAGE_CHARACTERISTIC_HANDLE, (uint8_t*)&value, sizeof(float), true);
    }
#endif
}

/**
* @brief RBC_MESH framework event handler. Defined in rbc_mesh.h. Handles
*   events coming from the mesh.
*
* @param[in] evt RBC event propagated from framework
*/
void rbc_mesh_event_handler(rbc_mesh_event_t* evt) {
	uint8_t brightness;
#ifdef CUBE_STORAGE
	storage_event_handler(evt);
#endif
	switch (evt->type) {
	case RBC_MESH_EVENT_TYPE_TX:
		break;
	case RBC_MESH_EVENT_TYPE_CONFLICTING_VAL:
	case RBC_MESH_EVENT_TYPE_NEW_VAL:
	case RBC_MESH_EVENT_TYPE_UPDATE_VAL:
		switch (evt->params.rx.value_handle) {
		case BRIGHTNESS_CHARACTERISTIC_HANDLE:
			if(memcmp(evt->params.rx.p_data, m_my_addr.addr, BLE_GAP_ADDR_LEN) == 0) {
				memcpy(&brightness, evt->params.rx.p_data+BLE_GAP_ADDR_LEN, sizeof(brightness));
				set_brightness_soft(brightness);
			}
			break;
		case REQUEST_DEVICEADDRESS_CHARACTERISTIC_HANDLE:
#ifdef VERBOSE
			SEGGER_RTT_printf(0, "request device addresses type:%d\n", evt->type);
#endif
			app_timer_stop(m_request_deviceaddress_timer_id);
			uint8_t rnd_delay;
			nrf_drv_rng_rand(&rnd_delay, 1);
			app_timer_start(m_request_deviceaddress_timer_id, rnd_delay*REQUEST_DEVICEADDRESSES_TIMEOUT, NULL);
			break;
		case REQUEST_BRIGHTNESS_CHARACTERISTIC_HANDLE:
			if(memcmp(evt->params.rx.p_data, m_my_addr.addr, BLE_GAP_ADDR_LEN) == 0) {
#ifdef VERBOSE
				print_data("request brightness for", evt->params.rx.p_data, BLE_GAP_ADDR_LEN);
#endif
				request_brightness();
			}
			break;
		default:
			break;
		}
		break;
	case RBC_MESH_EVENT_TYPE_INITIALIZED:
//		/* init BLE gateway softdevice application: */
//		nrf_adv_conn_init();
        break;
#ifdef MESH_DFU
    case RBC_MESH_EVENT_TYPE_DFU_BANK_AVAILABLE:
        dfu_bank_flash(evt->params.dfu.bank.dfu_type);
        break;

    case RBC_MESH_EVENT_TYPE_DFU_NEW_FW_AVAILABLE:
        dfu_request(evt->params.dfu.new_fw.dfu_type,
            &evt->params.dfu.new_fw.new_fwid,
            (uint32_t*) 0x24000);
        break;

    case RBC_MESH_EVENT_TYPE_DFU_RELAY_REQ:
        dfu_relay(evt->params.dfu.relay_req.dfu_type,
            &evt->params.dfu.relay_req.fwid);
        break;

    case RBC_MESH_EVENT_TYPE_DFU_START:
    case RBC_MESH_EVENT_TYPE_DFU_END:
        break;
    case RBC_MESH_EVENT_TYPE_DFU_SOURCE_REQ:
        break;
#endif
	}
}

/**@brief Function for the Timer initialization.
 *
 * @details Initializes the timer module.
 */
static void timers_init(void)
{
    // Initialize timer module, making it use the scheduler
    app_timer_init();

    // create request addresses timer
    APP_ERROR_CHECK(app_timer_create(&m_request_deviceaddress_timer_id,
                                APP_TIMER_MODE_SINGLE_SHOT,
                                request_deviceaddresses_timeout_handler));
    // create message timer
    APP_ERROR_CHECK(app_timer_create(&m_message_timer_id,
                                APP_TIMER_MODE_SINGLE_SHOT,
                                message_timeout_handler));
    // create notify error timer
    APP_ERROR_CHECK(app_timer_create(&m_notify_error_timer_id,
                                APP_TIMER_MODE_SINGLE_SHOT,
                                notify_error_timeout_handler));
    // create temperature timer
    APP_ERROR_CHECK(app_timer_create(&m_temperature_timer_id,
                                APP_TIMER_MODE_REPEATED,
                                temperature_timeout_handler));
}

#ifdef BOARD_PCA10028
static void button_event_handler(uint8_t pin_no, uint8_t button_action)
{
    static uint8_t button_state = 0;
    if(button_action)
    {
        button_state |= (1<<(pin_no-17));
    }
    else
    {
        button_state &= ~(1<<(pin_no-17));
    }
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "BUTTON %d state %d\n",pin_no, button_state );
#endif
	send_mesh_value(BUTTON_CHARACTERISTIC_HANDLE, &button_state, sizeof(button_state), true);
}
#endif

/**@brief Function for initializing the button handler module.
 */
static void buttons_init(void)
{
    // Note: Array must be static because a pointer to it will be saved in the Button handler
    //       module.file
#ifdef BOARD_PCA10028
    uint32_t err_code;
    static app_button_cfg_t buttons[] =
    {
        {BSP_BUTTON_0, false, BUTTON_PULL, button_event_handler},
        {BSP_BUTTON_1, false, BUTTON_PULL, button_event_handler},
        {BSP_BUTTON_2, false, BUTTON_PULL, button_event_handler},
        {BSP_BUTTON_3, false, BUTTON_PULL, button_event_handler}
    };
    err_code = app_button_init(buttons, sizeof(buttons) / sizeof(buttons[0]), BUTTON_DETECTION_DELAY);
    APP_ERROR_CHECK(err_code);
#endif
}

/** @brief main function */
int main(void)
{
#ifdef VERBOSE
#ifdef BOARD_PCA10028
    SEGGER_RTT_WriteString(0, "board 10028\n");
#endif
#ifdef BOARD_PCA10031
    SEGGER_RTT_WriteString(0, "board 10031\n");
#endif
#ifdef BOARD_BLUENODES
    SEGGER_RTT_WriteString(0, "board bluenodes\n");
#endif
#endif
    /* Enable Softdevice (including sd_ble before framework) */
	nrf_clock_lf_cfg_t clock_lf_cfg = MESH_CLOCK_SOURCE;
	// Initialize the SoftDevice handler module.
	SOFTDEVICE_HANDLER_INIT(&clock_lf_cfg, NULL);
    softdevice_ble_evt_handler_set(sd_ble_evt_handler);
	softdevice_sys_evt_handler_set(sys_evt_dispatch);

	gpio_init();
    timers_init();
    buttons_init();
	APP_ERROR_CHECK(peripheral_init(adc_value_notification_evt_handler));
//    scheduler_init();
    nrf_drv_rng_init(NULL);
#ifndef NO_BLE
    /* Init the rbc_mesh */
    rbc_mesh_init_params_t init_params;

    init_params.access_addr         = MESH_ACCESS_ADDR;
    init_params.interval_min_ms     = MESH_INTERVAL_MIN_MS;
    init_params.channel             = MESH_CHANNEL;
	init_params.lfclksrc            = clock_lf_cfg;
	init_params.tx_power            = RBC_MESH_TXPOWER_0dBm;

	APP_ERROR_CHECK(rbc_mesh_init(init_params));
#endif
    sd_ble_gap_addr_get(&m_my_addr);

#ifndef NO_BLE
	APP_ERROR_CHECK(rbc_mesh_value_enable(BRIGHTNESS_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(BUTTON_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(REQUEST_BRIGHTNESS_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(REQUEST_DEVICEADDRESS_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(ANSWER_BRIGHTNESS_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(ANSWER_DEVICEADDRESS_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(NOTIFY_DEVICE_ERROR_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(SET_TIME_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(CLEAR_STORAGE_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(REQUEST_STORAGE_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(ANSWER_STORAGE_CHARACTERISTIC_HANDLE));
	APP_ERROR_CHECK(rbc_mesh_value_enable(MESSAGE_CHARACTERISTIC_HANDLE));
#endif

#ifdef BOARD_PCA10028
    APP_ERROR_CHECK(app_button_enable());
#endif
#ifdef CUBE_STORAGE
    APP_ERROR_CHECK(cube_init(&m_my_addr));
#endif

#ifndef NO_BLE
	/* init BLE gateway softdevice application: */
	nrf_adv_conn_init(NODE_NAME, ble_connect_event_handler);
#ifdef BATTERY_SERVICE
    bas_init();
#endif
#ifdef SHOW_TEMPERATURE
    app_timer_start(m_temperature_timer_id, TEMPERATURE_TIMEOUT, NULL);
#endif
#endif
#ifdef VERBOSE
	SEGGER_RTT_WriteString(0, "init done\n");
#endif
	led_test();
	load_test();

    rbc_mesh_event_t evt;
    /* sleep */
    while (true)
    {
        sd_app_evt_wait();
#ifndef TEST_DEBUG
		if (rbc_mesh_event_get(&evt) == NRF_SUCCESS) {
			rbc_mesh_event_handler(&evt);
			rbc_mesh_event_release(&evt);
		}
#endif
#ifdef CUBE_STORAGE
        cube_store();
#endif
    }
}

