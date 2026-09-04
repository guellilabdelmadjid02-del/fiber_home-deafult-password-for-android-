#include "lib.h"

char map_char(char c)
{
    switch (c)
    {
    case '0': return 'f';
    case '1': return 'e';
    case '2': return 'd';
    case '3': return 'c';
    case '4': return 'b';
    case '5': return 'a';
    case '6': return '9';
    case '7': return '8';
    case '8': return '7';
    case '9': return '6';
    case 'f': return '0';
    case 'e': return '1';
    case 'd': return '2';
    case 'c': return '3';
    case 'b': return '4';
    case 'a': return '5';
    case 'F': return '0'; // Handle uppercase just in case
    case 'E': return '1';
    case 'D': return '2';
    case 'C': return '3';
    case 'B': return '4';
    case 'A': return '5';
    default: return 0;
    }
}
