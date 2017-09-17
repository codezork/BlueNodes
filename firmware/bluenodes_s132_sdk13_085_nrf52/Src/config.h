/***********************************************************************************
Copyright (c) Bluenodes GmbH
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are not permitted.
************************************************************************************/

#ifndef CONFIG_H_INCLUDED
#define CONFIG_H_INCLUDED

/* Debug macros for debugging with logic analyzer */
#define SET_PIN(x) NRF_GPIO->OUTSET = (1 << (x))
#define CLEAR_PIN(x) NRF_GPIO->OUTCLR = (1 << (x))
#define TICK_PIN(x) do { SET_PIN((x)); CLEAR_PIN((x)); }while(0)

#define APP_TIMER_OP_QUEUE_SIZE         10u                                           /**< Size of timer operation queues. */
#define BUTTON_DETECTION_DELAY          APP_TIMER_TICKS(200)    /**< Delay from a GPIOTE event until a button is reported as pushed (in number of timer ticks). */
#define SCHED_MAX_EVENT_DATA_SIZE       sizeof(app_timer_event_t)                   /**< Maximum size of scheduler events. Note that scheduler BLE stack events do not contain any data, as the events are being pulled from the stack in the event handler. */
#define SCHED_QUEUE_SIZE                10                                          /**< Maximum number of events in the scheduler queue. */

#define NOTIFICATION_TIMEOUT			APP_TIMER_TICKS(100u)
#define SOFT_BRIGHTNESS                 APP_TIMER_TICKS(30u)

#define DEAD_BEEF                       0xDEADBEEF                                  /**< Value used as error code on stack dump, can be used to identify stack location on stack unwind. */
#define NODE_NAME                       "BlueNode"

#endif /* CONFIG_H_INCLUDED */
