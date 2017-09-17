#ifndef BAS_ADC_H__
#define BAS_ADC_H__

#include <stdint.h>
#include <stdbool.h>
#include "ble.h"
#include "ble_srv_common.h"

uint32_t bas_init(void);
void battery_level_update(uint8_t battery_level);
void bas_adc_on_ble_evt(ble_evt_t * p_ble_evt);
#endif		// BAS_ADC_H__
