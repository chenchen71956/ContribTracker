#

## 1

### ADD命令

```只能邀请在线的玩家，如果在创建时指定玩家则直接添加
- add {type} + {name} + {player}        -创建
- add {type} + {name}                   -创建
- adde {id} + {player}                  -邀请(直接添加)
```

### Delete命令

```所有人可执行，但只能删除自己创建的id(管理员可删除任意ID)
- delete {id}                           -删除某个贡献(包括贡献下的玩家树)
···

### remove命令

```任何人可执行，但只能删除自己邀请的玩家(管理和创建人允许删除这个贡献下的所有玩家)
- remove {id} + {player}                -移除某个贡献的某个玩家
```

### list命令

```任何人可执行
- list                                  -列出所有贡献
```

### near命令，别名n

```任何人可执行
-near                                   -列出半径32格内所有贡献
-n                                      -列出半径32格内所有贡献
```
