#ifndef PERIPHERAL_H_INCLUDED
#define PERIPHERAL_H_INCLUDED

/** the pins **/
#define LOAD			 6
#ifdef BOARD_BLUENODES
#define ZERO		    17
#endif
#ifdef BOARD_PCA10028
#define ZERO		    15
#endif
#define ZERO_BRIDGE     16
#define OCD_PIN          7     // over current signal.
#define SPIM0_SS_PIN     8     // SPI CSN signal.
#define SPIM0_SCK_PIN    9     // SPI SCK signal.
#define SPIM0_MISO_PIN  10     // SPI MISO signal.

#define VOLTAGE				230.0f
#define MAX_AMPERE          5.0f
#define AVG_POWER           2.0f
#define MINUTES_PER_HOUR    60.0f
#define UPPER_LIMIT         167.0f     //** equals 1.18V */
#define LOWER_LIMIT         72.0f     //** equals 0.51V */
#define ZERO_AMPERE_UNITS   119
#define OFFSET              0.75f
#define BATTERY_UPDATE_COUNT    3000

#define ADC_REF_VOLTAGE_IN_MILLIVOLTS   600                                     /**< Reference voltage (in milli volts) used by ADC while doing conversion. */
#define ADC_PRE_SCALING_COMPENSATION    6                                       /**< The ADC is configured to use VDD with 1/3 prescaling as input. And hence the result of conversion is to be multiplied by 3 to get the actual value of the battery voltage.*/
#define DIODE_FWD_VOLT_DROP_MILLIVOLTS  270                                     /**< Typical forward voltage drop of the diode . */
#define ADC_RES_10BIT                   1024                                    /**< Maximum digital value for 10-bit ADC conversion. */
#define ADC_RESULT_IN_MILLI_VOLTS(ADC_VALUE)\
        ((((ADC_VALUE) * ADC_REF_VOLTAGE_IN_MILLIVOLTS) / ADC_RES_10BIT) * ADC_PRE_SCALING_COMPENSATION)


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

/**@brief Connection notification event handler type. */
typedef void (*value_notification_evt_handler_t) (float value, bool isMinute);

void gpio_init(void);
uint32_t peripheral_init(value_notification_evt_handler_t evt_handler);
void request_brightness();
void reset_brightness();
void set_brightness(uint8_t brightness);
void set_brightness_soft(uint8_t brightness);
void led_test(void);
void load_test(void);
float calc_watt_hour(float value);
void store_per_second(float value);
void soft_brightness_timeout_handler(void * p_context);
void set_ocd();
void reset_ocd();
bool check_parity(uint16_t x);
bool decode_value(uint16_t value);
#endif /* PERIPHERAL_H_INCLUDED */
