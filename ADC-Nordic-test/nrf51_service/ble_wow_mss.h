//=====================================================================================================================
/*---------------------------------------------------------------------------------------------------------*/
/* Module Setting Service(0xFF30)                                                                          */
/* --Reboot Characteristic(0xFF31)                                      W   1                              */
/*---------------------------------------------------------------------------------------------------------*/
#ifndef __WW_BLE_MSS_H__
#define __WW_BLE_MSS_H__

#include <stdint.h>
#include <stdbool.h>
#include "ble.h"
#include "ble_srv_common.h"
/*---------------------------------------------------------------------------------------------------------*/
/* Define Some Setting                                                                                     */
/*---------------------------------------------------------------------------------------------------------*/
#define MSS_REBOOT_APP        0x01
#define MSS_REBOOT_DFU        0x02
/*---------------------------------------------------------------------------------------------------------*/
/* Define Photo Setting Service structure. This contains various status information for the service.       */
/*---------------------------------------------------------------------------------------------------------*/
typedef struct ble_wow_mss_s
{
    uint16_t                 conn_handle;
    uint16_t                 service_handle;
    ble_gatts_char_handles_t ble_mss_reboot_char_handles;
} ble_wow_mss_t;
/*---------------------------------------------------------------------------------------------------------*/
/* Declare Some Function                                                                                   */
/*---------------------------------------------------------------------------------------------------------*/
void ble_mss_on_ble_evt(ble_evt_t * p_ble_evt);
void ble_mss_init(void);

#endif // __WW_BLE_MSS_H__
//=====================================================================================================================
