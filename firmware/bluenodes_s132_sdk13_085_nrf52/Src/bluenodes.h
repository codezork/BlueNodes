#ifndef BLUENODES_H
#define BLUENODES_H

// LEDs definitions for BLUENODES
#define LEDS_NUMBER    2

#define LED_START      14
#define LED_RGB_RED    13
#define LED_RGB_BLUE   14
#define LED_STOP       14

#define LED_RGB_RED_MASK    (1<<LED_RGB_RED)
#define LED_RGB_BLUE_MASK   (1<<LED_RGB_BLUE)

#define LEDS_LIST { LED_RGB_RED, LED_RGB_BLUE}
// defining RGB led as 3 single LEDs
#define BSP_LED_0 LED_RGB_RED
#define BSP_LED_1 LED_RGB_BLUE

#define BSP_LED_0_MASK    (1<<BSP_LED_0)
#define BSP_LED_1_MASK    (1<<BSP_LED_1)

#define LEDS_MASK      (BSP_LED_0_MASK | BSP_LED_1_MASK)
//defines which LEDs are lit when signal is low
#define LEDS_INV_MASK  LEDS_MASK

// there are no user buttons
#define BUTTONS_NUMBER 0
#define BUTTONS_LIST {}
#define BUTTONS_MASK   0x00000000

// Low frequency clock source to be used by the SoftDevice
#ifdef S210
#define NRF_CLOCK_LFCLKSRC      NRF_CLOCK_LFCLKSRC_XTAL_20_PPM
#else
#define NRF_CLOCK_LFCLKSRC      {.source        = NRF_CLOCK_LF_SRC_XTAL,            \
                                 .rc_ctiv       = 0,                                \
                                 .rc_temp_ctiv  = 0,                                \
                                 .xtal_accuracy = NRF_CLOCK_LF_XTAL_ACCURACY_20_PPM}
#endif

// UART connection with J-Link
#define RX_PIN_NUMBER  11
#define TX_PIN_NUMBER  9
#define CTS_PIN_NUMBER 10
#define RTS_PIN_NUMBER 8
#define HWFC           true

#endif
