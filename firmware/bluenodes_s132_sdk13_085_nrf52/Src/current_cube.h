#ifndef CURRENT_CUBE_H__
#define CURRENT_CUBE_H__

#ifdef CUBE_STORAGE
#include "rbc_mesh.h"
#define FILE_ID     0x11
#define REC_ID      0x22

#define MAX_STORAGE_CONTENT 12

typedef enum {
    STORAGE_INIT_OP_CODE,
    STORAGE_STORE_OP_CODE,
    STORAGE_LOAD_OP_CODE,
    STORAGE_CLEAR_OP_CODE
} cube_operations_t;

typedef struct
{
    uint8_t level;
	uint8_t part;
	uint8_t length;
	uint8_t startstop;
	uint8_t buffer[MAX_STORAGE_CONTENT];
} storage_data_t;

typedef struct
{
    uint8_t level;
    uint8_t start_part;
    uint8_t end_part;
} level_info_t;

static const uint8_t id_code[] = {0xC0, 0xFF, 0xEE, 0xBA, 0xBE, 0xEE};
#define ID_LEN				6
#define DEFAULT_SIZE		256

#define ENTRIES_MIN			60
#define MINUTE_ENTRIES		ENTRIES_MIN					//60
#define ENTRIES_HOUR		24
#define ENTRIES_DAY			32
#define SHORT_ENTRIES		(ENTRIES_HOUR+ENTRIES_DAY)	//56
#define ENTRIES_MONTH		12
#define ENTRIES_YEAR		30
#define LONG_ENTRIES		(ENTRIES_MONTH+ENTRIES_YEAR)//42

enum
{
	MINUTE	= 0,
	DAY		= 1,
	YEAR	= 2
};
#define MAX_BLOCKS			3

static const uint16_t SIZES[MAX_BLOCKS] = {MINUTE_ENTRIES*sizeof(float), SHORT_ENTRIES*sizeof(float), LONG_ENTRIES*sizeof(float)};

typedef struct
{
	float *minute;
	float *hour;
	float *day;
	float *month;
	float *year;
} bn_time_t;

void cube_dump(char *hint);
void cube_set_power(float wattminute);
void cube_set_time(uint8_t *buffer, uint8_t length);
void cube_set_time_internal(uint8_t year, uint8_t month, uint8_t day, uint8_t hour, uint8_t min);
void cube_incr_time(void);
uint32_t cube_init(ble_gap_addr_t *gap_address);
uint32_t cube_operate(uint8_t opcode);
void cube_get_storage(storage_data_t *data);
level_info_t cube_get_level_info(uint8_t level);
void storage_event_handler(rbc_mesh_event_t* evt);
void request_storage_timeout_handler(void * p_context);
void cube_store(void);
#endif
#endif
