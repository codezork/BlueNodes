#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include "nordic_common.h"
#include "nrf_error.h"
#include "nrf_soc.h"
#include "app_timer.h"
#include "app_scheduler.h"
#include "app_util_platform.h"
#include "current_cube.h"
#include "config.h"
#include "common.h"
#include "rbc_mesh.h"
#include "fds.h"
#include "SEGGER_RTT.h"

#ifdef CUBE_STORAGE
//#define CUBEDUMP
// storage of metadata
static uint8_t 					m_buffer[MAX_BLOCKS][256]  __attribute__((aligned(4)));
static bn_time_t				m_now;
static bool						time_is_set = false;
static level_info_t             level_map[MAX_BLOCKS] = {{MINUTE,0,19},{DAY,20,38},{YEAR,39,52}};
static volatile bool			must_clear = false;
static volatile bool			must_store = false;
static uint8_t                  last_level = 0;
static uint8_t                  last_part = 0;
static ble_gap_addr_t           address;
static uint8_t                  m_year, m_month, m_day, m_hour, m_minute;
static volatile ret_code_t      fds_result = -1;

APP_TIMER_DEF(m_request_storage_timer_id);
#define CUBE_TIMEOUT    					            APP_TIMER_TICKS(60000)
APP_TIMER_DEF(m_cube_timer_id);

/*
    Part| Type	| Range
    0	| min	|  1..3
    1	| min	|  4..6
    2	| min	|  7..9
    3	| min	| 10..12
    4	| min	| 13..15
    5	| min	| 16..18
    6	| min	| 19..21
    7	| min	| 22..24
    8	| min	| 25..27
    9	| min	| 28..30
    10	| min	| 31..33
    11	| min	| 34..36
    12	| min	| 37..39
    13	| min	| 40..42
    14	| min	| 43..45
    15	| min	| 46..48
    16	| min	| 49..51
    17	| min	| 52..54
    18	| min	| 55..57
    19	| min	| 58..60
    20	| hour	|  1..3
    21	| hour	|  4..6
    22	| hour	|  7..9
    23	| hour	| 10..12
    24	| hour	| 13..15
    25	| hour	| 16..18
    26	| hour	| 19..21
    27	| hour	| 22..24
    28	| day	|  1..3
    29	| day	|  4..6
    30	| day	|  7..9
    31	| day	| 10..12
    32	| day	| 13..15
    33	| day	| 16..18
    34	| day	| 19..21
    35	| day	| 22..24
    36	| day	| 25..27
    37	| day	| 28..30
    38	| day	| 31..33
    39	| month	|  1..3
    40	| month	|  4..6
    41	| month	|  7..9
    42	| month	| 10..12
    43	| year	|  1..3
    44	| year	|  4..6
    45	| year	|  7..9
    46	| year	| 10..12
    47	| year	| 13..15
    48	| year	| 16..18
    49	| year	| 19..21
    50	| year	| 22..24
    51	| year	| 25..27
    52	| year	| 28..30	wrap around occurs after 30 years
    */

// Simple event handler to handle errors during initialization.
static void fds_evt_handler(fds_evt_t const * const p_fds_evt)
{
    fds_result = p_fds_evt->result;
}

void clear_buffer() {
    memset(m_buffer, 0, DEFAULT_SIZE*MAX_BLOCKS);
}

void cube_timeout_handler(void * p_context) {
    must_store = true;
}

uint32_t cube_init(ble_gap_addr_t *gap_address)
{
    memcpy(&address, gap_address, sizeof(ble_gap_addr_t));

    cube_operate(STORAGE_INIT_OP_CODE);

    time_is_set = false;

    // init memory for first usage
    bool must_init = false;
    uint32_t error_code = cube_operate(STORAGE_LOAD_OP_CODE);
    // prefix check
    if(error_code != FDS_SUCCESS || memcmp(m_buffer[MINUTE]+SIZES[MINUTE], id_code, ID_LEN*sizeof(uint8_t)) != 0)
    {
#ifdef VERBOSE
        SEGGER_RTT_printf(0, "cube must init due to missing id\n");
#endif
        must_init = true;
    }
    if(must_init)
    {
        cube_operate(STORAGE_CLEAR_OP_CODE);
        // init prefix check
        clear_buffer();
        for(int block = 0; block<MAX_BLOCKS; block ++)
        {
            memcpy(m_buffer[block]+SIZES[block], id_code, ID_LEN*sizeof(uint8_t));
        }
        cube_operate(STORAGE_STORE_OP_CODE);
    }
#ifdef VERBOSE
    else
    {
        SEGGER_RTT_printf(0, "cube not cleared\n");
    }
#endif
    APP_ERROR_CHECK(app_timer_create(&m_request_storage_timer_id,
            APP_TIMER_MODE_REPEATED,
            request_storage_timeout_handler));
    // create cube timer
    APP_ERROR_CHECK(app_timer_create(&m_cube_timer_id,
            APP_TIMER_MODE_REPEATED,
            cube_timeout_handler));
    if (error_code != NRF_SUCCESS)
    {
        return error_code;
    }
    return NRF_SUCCESS;
}

uint32_t cube_operate(uint8_t opcode)
{
    fds_record_t        record;
    fds_record_desc_t   record_desc;
    fds_record_chunk_t  record_chunk;
    fds_flash_record_t  flash_record;
    fds_find_token_t    ftok;

    uint32_t error_code = FDS_SUCCESS;


    switch(opcode)
    {
    case STORAGE_INIT_OP_CODE:
#ifdef VERBOSE
        SEGGER_RTT_printf(0, "init\n");
#endif
        error_code = fds_register(fds_evt_handler);
        if(error_code == FDS_SUCCESS ) {
            fds_result = -1;
            error_code = fds_init();
            while(error_code == FDS_SUCCESS && fds_result == -1) {;}
        }
        break;
    case STORAGE_STORE_OP_CODE:
#ifdef VERBOSE
        SEGGER_RTT_printf(0, "storing\n");
#endif
        // Set up data.
        for(int block=0; block<MAX_BLOCKS && error_code == FDS_SUCCESS; block++)
        {
            fds_result = -1;
            record_chunk.p_data         = (uint8_t *)m_buffer[block];
            record_chunk.length_words   = DEFAULT_SIZE/2;
            // Set up record.
            record.file_id           = FILE_ID;
            record.key               = REC_ID+block;
            record.data.p_chunks     = &record_chunk;
            record.data.num_chunks   = 1;

            error_code = fds_record_write(&record_desc, &record);
            if (error_code == FDS_ERR_NO_SPACE_IN_FLASH) {
                fds_result = -1;
                error_code = fds_gc();
                while(error_code == FDS_SUCCESS && fds_result == -1) {;}
                error_code = fds_record_write(&record_desc, &record);
            }
        }
        break;
    case STORAGE_LOAD_OP_CODE:
#ifdef VERBOSE
        SEGGER_RTT_printf(0, "loading\n");
#endif
        clear_buffer();
        int block=0;
        memset(&ftok, 0x00, sizeof(fds_find_token_t));
        while(fds_record_find(FILE_ID, REC_ID+block, &record_desc, &ftok) == FDS_SUCCESS) {
#ifdef VERBOSE
            SEGGER_RTT_printf(0, "loading page %d\n", block+1);
#endif
            error_code = fds_record_open(&record_desc, &flash_record);
            if(error_code == FDS_SUCCESS) {
                memcpy((uint8_t *)m_buffer[block], flash_record.p_data, DEFAULT_SIZE);
                fds_record_close(&record_desc);
#ifdef VERBOSE
                SEGGER_RTT_printf(0, "loading page %d successful. next page %d\n", block+1, ftok.page);
#endif
            }
            block ++;
            if(block >= MAX_BLOCKS) {
                break;
            }
        }
        break;
    case STORAGE_CLEAR_OP_CODE:
#ifdef VERBOSE
        SEGGER_RTT_printf(0, "clearing\n");
#endif
        clear_buffer();
        fds_result = -1;
        error_code = fds_file_delete(FILE_ID);
        while(error_code == FDS_SUCCESS && fds_result == -1) {;}
        break;
    default:
        break;
    }
    return error_code;
}

void cube_dump(char *hint)
{
    char number[20];
    uint16_t pre, post;
    SEGGER_RTT_printf(0, "%s\n", hint);
    for(int block=0; block<MAX_BLOCKS; block++)
    {
        SEGGER_RTT_printf(0, "block %d size:%d\n", block, SIZES[block]);
        float *ptr = (float*)m_buffer[block];
        while(ptr < (float*)(m_buffer[block]+SIZES[block]))
        {
            pre = (uint16_t)*ptr;
            post = (*ptr - pre) * 1000;
            sprintf(number, "%d.%d|", pre, post);
            SEGGER_RTT_printf(0, "%s", number);
            ptr ++;
        }
        SEGGER_RTT_printf(0, "\n");
//		for(int i=0; i<ID_LEN; i++)
//		{
//			SEGGER_RTT_printf(0, "%02X", m_buffer[block][i+SIZES[block]]);
//		}
//		SEGGER_RTT_printf(0, "\n");
    }
}

void cube_set_power(float wattminute)
{
    *m_now.minute = wattminute;
    *m_now.hour += wattminute/1000;
    *m_now.day += wattminute/1000;
    *m_now.month += wattminute/1000;
    *m_now.year += wattminute/1000;
}

void cube_set_time(uint8_t *buffer, uint8_t length)
{
    if(length == 5)
    {
        cube_set_time_internal((uint8_t)*buffer, 						// year last 2 digits
                               (uint8_t)*(buffer+sizeof(uint8_t)), 	// month
                               (uint8_t)*(buffer+2*sizeof(uint8_t)), 	// day
                               (uint8_t)*(buffer+3*sizeof(uint8_t)), 	// hour
                               (uint8_t)*(buffer+4*sizeof(uint8_t)));	// minute
    }
    else
    {
#ifdef VERBOSE
        SEGGER_RTT_printf(0, "time format invalid len:%d\n", length);
#endif
    }
}

void cube_time2addr(void) {
    m_now.minute= (float*)m_buffer[MINUTE]+m_minute;
    m_now.hour 	= (float*)m_buffer[DAY]+m_hour;
    m_now.day 	= (float*)m_buffer[DAY]+(ENTRIES_HOUR+m_day-1);
    m_now.month	= (float*)m_buffer[YEAR]+m_month-1;
    m_now.year	= (float*)m_buffer[YEAR]+(ENTRIES_MONTH+m_year-16);
}

void cube_set_time_internal(uint8_t year, uint8_t month, uint8_t day, uint8_t hour, uint8_t minute)
{
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "time is  20%02d-%02d-%02d %02d:%02d\n", year, month, day, hour, minute);
    if(time_is_set) {
        SEGGER_RTT_printf(0, "time was 20%02d-%02d-%02d %02d:%02d\n", m_year, m_month, m_day, m_hour, m_minute);
        if(m_minute != minute) {
            SEGGER_RTT_printf(0, "minute deviation is %d\n", m_minute-minute);
        }
        if(m_hour != hour) {
            SEGGER_RTT_printf(0, "hour deviation is %d\n", m_hour-hour);
        }
        if(m_day != day) {
            SEGGER_RTT_printf(0, "day deviation is %d\n", m_day-day);
        }
        if(m_month != month) {
            SEGGER_RTT_printf(0, "month deviation is %d\n", m_month-month);
        }
        if(m_year != year) {
            SEGGER_RTT_printf(0, "year deviation is %d\n", m_year-year);
        }
    }
#endif
    m_year = year;
    m_month = month;
    m_day = day;
    m_hour = hour;
    m_minute = minute;
    cube_time2addr();
    if(!time_is_set) {
        app_timer_start(m_cube_timer_id, CUBE_TIMEOUT, NULL);
    }
    time_is_set = true;
}

void cube_incr_time(void)
{
    m_now.minute ++;
    m_minute ++;
    if(m_now.minute - (float*)m_buffer[MINUTE] == ENTRIES_MIN)
    {
        m_now.minute = (float*)m_buffer[MINUTE];
        m_minute = 0;
        for(float *p = m_now.minute; p<m_now.minute+ENTRIES_MIN; p++) *p = 0;
        m_now.hour ++;
        m_hour ++;
        if(m_now.hour - (float*)m_buffer[DAY] == ENTRIES_HOUR)
        {
            m_now.hour = (float*)m_buffer[DAY];
            m_day = 0;
            for(float *p = m_now.hour; p<m_now.hour+ENTRIES_HOUR; p++) *p = 0;
            m_now.day ++;
            if(m_now.day - (float*)m_buffer[DAY]+ENTRIES_HOUR == ENTRIES_DAY)
            {
                m_now.day = (float*)m_buffer[DAY]+ENTRIES_HOUR;
                m_day = 0;
                for(float *p = m_now.day; p<m_now.day+ENTRIES_DAY; p++) *p = 0;
                m_now.month ++;
                m_month ++;
                if(m_now.month - (float*)m_buffer[YEAR] == ENTRIES_MONTH)
                {
                    m_now.month = (float*)m_buffer[YEAR];
                    m_month = 0;
                    for(float *p = m_now.month; p<m_now.month+ENTRIES_MONTH; p++) *p = 0;
                    m_now.year ++;
                    m_year ++;
                    if(m_now.year - (float*)m_buffer[YEAR]+ENTRIES_MONTH == ENTRIES_YEAR)
                    {
                        // wrap around after 50 years
                        m_now.year = (float*)m_buffer[YEAR]+ENTRIES_MONTH;
                        for(float *p = m_now.year; p<m_now.year+ENTRIES_YEAR; p++) *p = 0;
                    }
                }
            }
        }
    }
    SEGGER_RTT_printf(0, "time now 20%02d-%02d-%02d %02d:%02d\n", m_year, m_month, m_day, m_hour, m_minute);
}

void cube_get_storage(storage_data_t *data)
{
    memset(data->buffer, 0, MAX_STORAGE_CONTENT);
    data->length = 3*sizeof(float);
    if(level_map[data->level].start_part==data->part)
    {
        data->startstop = 1;
    }
    else if(level_map[data->level].end_part==data->part)
    {
        data->startstop = 2;
    }
    else
    {
        data->startstop = 0;
    }
    memcpy(data->buffer, m_buffer[data->level]+(data->part-level_map[data->level].start_part)*data->length, data->length);
}

level_info_t cube_get_level_info(uint8_t level)
{
    return level_map[level];
}

void request_storage_timeout_handler(void * p_context)
{
    storage_data_t data;

    data.level = last_level;
    data.part = last_part;
    cube_get_storage(&data);
#ifdef VERBOSE
    SEGGER_RTT_printf(0, "answer storage for level %d part %d length:%d startstop:%d\n", data.level, data.part, data.length, data.startstop);
#endif
    send_mesh_value(ANSWER_STORAGE_CHARACTERISTIC_HANDLE, (uint8_t*)&data, sizeof(storage_data_t), false);
    if(last_part == cube_get_level_info(last_level).end_part)
        app_timer_stop(m_request_storage_timer_id);
    else
        last_part ++;
}

void cube_store(void) {
    if(time_is_set && (must_store||must_clear)) {
        // move time forward
        cube_incr_time();
#ifdef CUBEDUMP
        cube_dump("set power");
#endif
        if(must_clear)
        {
            cube_operate(STORAGE_CLEAR_OP_CODE);
        } else {
            cube_operate(STORAGE_STORE_OP_CODE);
        }
    }
    must_store = false;
    must_clear = false;
}

void storage_event_handler(rbc_mesh_event_t* evt)
{
    switch (evt->type)
    {
    case RBC_MESH_EVENT_TYPE_TX:
    case RBC_MESH_EVENT_TYPE_INITIALIZED:
        break;
    case RBC_MESH_EVENT_TYPE_CONFLICTING_VAL:
    case RBC_MESH_EVENT_TYPE_NEW_VAL:
    case RBC_MESH_EVENT_TYPE_UPDATE_VAL:
        switch (evt->params.rx.value_handle)
        {
        case SET_TIME_CHARACTERISTIC_HANDLE:
#ifdef VERBOSE
            SEGGER_RTT_printf(0, "set time\n");
#endif
            cube_set_time(evt->params.rx.p_data+BLE_GAP_ADDR_LEN, evt->params.rx.data_len-BLE_GAP_ADDR_LEN);
            break;
        case CLEAR_STORAGE_CHARACTERISTIC_HANDLE:
#ifdef VERBOSE
            SEGGER_RTT_printf(0, "clear storage\n");
#endif
            must_clear = true;
            break;
        case REQUEST_STORAGE_CHARACTERISTIC_HANDLE:
#ifdef VERBOSE
            SEGGER_RTT_printf(0, "request storage\n");
#endif
            if(memcmp(evt->params.rx.p_data, &address.addr, BLE_GAP_ADDR_LEN) == 0)
            {
#ifdef VERBOSE
                SEGGER_RTT_printf(0, "request storage address ok\n");
                print_data("request storage for ", evt->params.rx.p_data, BLE_GAP_ADDR_LEN);
#endif
                last_level = *((uint8_t*)evt->params.rx.p_data+BLE_GAP_ADDR_LEN);
                last_part = cube_get_level_info(last_level).start_part;
                app_timer_start(m_request_storage_timer_id, NOTIFICATION_TIMEOUT, 0);
            }
            break;
        }
        default:
            break;
    }
}
#endif // CUBE_STORAGE
