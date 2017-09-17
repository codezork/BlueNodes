/***********************************************************************************
Copyright (c) Nordic Semiconductor ASA
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or
  other materials provided with the distribution.

  3. Neither the name of Nordic Semiconductor ASA nor the names of other
  contributors to this software may be used to endorse or promote products
  derived from this software without specific prior written permission.

  4. This software must only be used in a processor manufactured by Nordic
  Semiconductor ASA, or in a processor manufactured by a third party that
  is used in combination with a processor manufactured by Nordic Semiconductor.


THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
************************************************************************************/

#include "nrf_adv_conn.h"
#include "led_config.h"

#include "rbc_mesh.h"

#include "ble.h"
#include "ble_advdata.h"
#include "nrf_assert.h"
#include "nrf_gpio.h"
#include "app_error.h"
#include "SEGGER_RTT.h"

#include <stdbool.h>
#include <string.h>
#include <stdio.h>

#define BLE_ADV_INTERVAL_750MS (800)
#define BLE_ADV_INTERVAL_200MS (320)

/*****************************************************************************
* Static Globals
*****************************************************************************/

/* Advertisement data */
//static uint8_t ble_adv_man_data[] = {0x01 /* PDU_ID */, 0xA0, 0xA1, 0xA2, 0xA3};

/* BLE advertisement parameters */
static ble_gap_adv_params_t ble_adv_params =
{
    BLE_GAP_ADV_TYPE_ADV_IND,  /* Use ADV_IND advertisements */
    NULL,                      /* Not used for this type of advertisement */
    BLE_GAP_ADV_FP_ANY,        /* Don't filter */
//    NULL,                      /* Whitelist not in use */
    BLE_ADV_INTERVAL_200MS,    /* Advertising interval set to intentionally disrupt the timeslot example */
    0,                         /* Timeout in seconds */
    {
        0, 0, 0
    }
};

static ble_advdata_t ble_adv_data;
static ble_gap_sec_params_t ble_gap_bond_params =
{
    .bond = 0,                         /* Don't perform bonding */
    .mitm = 0,                         /* Man-in-the-middle protection not required */
    .io_caps = BLE_GAP_IO_CAPS_NONE,   /* No I/O capabilities */
    .oob = 0,                          /* Out-of-band data not available */
    .min_key_size = 7,                 /* Minimum encryption key size */
    .max_key_size = 16                 /* Maximum encryption key size */
};
static ble_connect_evt_handler_t m_evt_handler  = NULL;   /**< Application event handler for handling connection events. */

/*****************************************************************************
* Static Functions
*****************************************************************************/

static void ble_gatts_event_handler(ble_evt_t* evt)
{
    switch (evt->header.evt_id)
    {
    case BLE_GATTS_EVT_RW_AUTHORIZE_REQUEST:
        break;

    case BLE_GATTS_EVT_SYS_ATTR_MISSING:
        sd_ble_gatts_sys_attr_set(evt->evt.gatts_evt.conn_handle, NULL, 0, 0);
        break;

    case BLE_GATTS_EVT_WRITE:
        break;

    default:
        break;
    }
}

static void ble_gap_event_handler(ble_evt_t* evt)
{
    switch (evt->header.evt_id)
    {
    case BLE_GAP_EVT_CONNECTED:
        if(m_evt_handler != NULL) {
            m_evt_handler(true);
        }
        break;

    case BLE_GAP_EVT_DISCONNECTED:
        sd_ble_gap_adv_start(&ble_adv_params, NULL);
        if(m_evt_handler != NULL) {
            m_evt_handler(false);
        }
        break;

    case BLE_GAP_EVT_SEC_PARAMS_REQUEST:
        APP_ERROR_CHECK(sd_ble_gap_sec_params_reply(evt->evt.gap_evt.conn_handle,
                        BLE_GAP_SEC_STATUS_SUCCESS, &ble_gap_bond_params, NULL));
        break;

    case BLE_GAP_EVT_CONN_SEC_UPDATE:
        break;

    case BLE_GAP_EVT_AUTH_STATUS:
        break;

    default:
        break;
    }
}

/*****************************************************************************
* Interface Functions
*****************************************************************************/

void nrf_adv_conn_init(char *device_name, ble_connect_evt_handler_t evt_handler)
{
    uint32_t err_code;

    m_evt_handler = evt_handler;
     /* Fill advertisement data struct: */
    uint8_t flags = BLE_GAP_ADV_FLAG_BR_EDR_NOT_SUPPORTED |
                    BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE;

    memset(&ble_adv_data, 0, sizeof(ble_adv_data));

    ble_adv_data.flags = flags;
    ble_adv_data.name_type    = BLE_ADVDATA_FULL_NAME;
    //ble_adv_data.p_manuf_specific_data = &man_data;

    ble_gap_conn_sec_mode_t name_sec_mode = {1, 1};
    ble_gap_addr_t my_addr;

    #if (NRF_SD_BLE_API_VERSION >= 3)
        err_code = sd_ble_gap_addr_get(&my_addr);
    #else
        err_code = sd_ble_gap_address_get(&my_addr);
    #endif
    APP_ERROR_CHECK(err_code);

    char name[64];
    sprintf(name, "%s %d", device_name,
            ((uint16_t) my_addr.addr[4] << 8) | (my_addr.addr[5]));

    err_code = sd_ble_gap_device_name_set(&name_sec_mode, (uint8_t*) name, strlen(name));
    APP_ERROR_CHECK(err_code);

    /* Set advertisement data with ble_advdata-lib */
    err_code = ble_advdata_set(&ble_adv_data, NULL);
    APP_ERROR_CHECK(err_code);

    /* Start advertising */
    err_code = sd_ble_gap_adv_start(&ble_adv_params, NULL);
    APP_ERROR_CHECK(err_code);
}

void nrf_adv_conn_evt_handler(ble_evt_t* evt)
{
    switch (evt->header.evt_id & 0xF0)
    {
    case BLE_GAP_EVT_BASE:
        ble_gap_event_handler(evt);
        break;

    case BLE_GATTS_EVT_BASE:
        ble_gatts_event_handler(evt);
        break;

    default:
        break;
    }
}
