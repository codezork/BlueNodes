#include "bas_adc.h"
#include "ble_bas.h"
#include <string.h>
#include "nordic_common.h"
#include "ble_srv_common.h"
#include "app_util.h"
#include "app_scheduler.h"
#include "ble_bas.h"
#include "nrf_sdm.h"
#include "SEGGER_RTT.h"

static	ble_bas_t 						m_bas = {NULL,0,{0,0,0,0},0,0,0,false};

/**@brief Function for performing battery measurement and updating the Battery Level characteristic
 *        in Battery Service.
 */
void battery_level_update(uint8_t battery_level)
{
#ifndef NO_BLE
    uint32_t err_code;
    err_code = ble_bas_battery_level_update(&m_bas, battery_level);
    if ((err_code != NRF_SUCCESS) &&
            (err_code != NRF_ERROR_INVALID_STATE) &&
            (err_code != BLE_ERROR_GATTS_SYS_ATTR_MISSING)
       )
    {
        APP_ERROR_HANDLER(err_code);
    }
#endif
}

void bas_adc_on_ble_evt(ble_evt_t * p_ble_evt)
{
    ble_bas_on_ble_evt(&m_bas, p_ble_evt);
}

// Initialize Battery Service.
uint32_t bas_init(void)
{
    ble_bas_init_t  bas_init;

    memset(&bas_init, 0, sizeof(bas_init));
    // Here the sec level for the Battery Service can be changed/increased.
    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&bas_init.battery_level_char_attr_md.cccd_write_perm);
    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&bas_init.battery_level_char_attr_md.read_perm);
    BLE_GAP_CONN_SEC_MODE_SET_NO_ACCESS(&bas_init.battery_level_char_attr_md.write_perm);

    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&bas_init.battery_level_report_read_perm);

    bas_init.evt_handler          = NULL;
    bas_init.support_notification = true;
    bas_init.p_report_ref         = NULL;
    bas_init.initial_batt_level   = 100;

    return ble_bas_init(&m_bas, &bas_init);
}

