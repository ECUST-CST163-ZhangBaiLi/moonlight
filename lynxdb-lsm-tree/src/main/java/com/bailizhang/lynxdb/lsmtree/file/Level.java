package com.bailizhang.lynxdb.lsmtree.file;

import com.bailizhang.lynxdb.core.log.LogGroup;
import com.bailizhang.lynxdb.core.utils.FileUtils;
import com.bailizhang.lynxdb.core.utils.NameUtils;
import com.bailizhang.lynxdb.lsmtree.common.*;
import com.bailizhang.lynxdb.lsmtree.memory.MemTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

public class Level {
    public static final int LEVEL_SSTABLE_COUNT = 10;

    private final LogGroup valueFileGroup;
    private final String parentDir;
    private final Path baseDir;
    private final int levelNo;
    private final LevelTree parent;
    private final Options options;

    private List<SsTable> ssTables = new ArrayList<>(LEVEL_SSTABLE_COUNT);

    public Level(String dir, int level, LevelTree levelTree, LogGroup logGroup, Options lsmOptions) {
        parentDir = dir;
        baseDir = Path.of(dir, String.valueOf(level));
        FileUtils.createDirIfNotExisted(baseDir.toFile());

        levelNo = level;
        parent = levelTree;
        valueFileGroup = logGroup;
        options = lsmOptions;

        List<String> subs = FileUtils.findSubFiles(dir);
        for(String sub : subs) {
            int id = NameUtils.id(sub);

            SsTable ssTable = new SsTable(
                    baseDir.toString(),
                    id,
                    levelNo,
                    valueFileGroup,
                    options
            );

            ssTables.add(ssTable);
        }
    }

    public void merge(MemTable immutable) {
        if(isFull()) {
            mergeToNextLevel();
        }

        SsTable ssTable = new SsTable(
                baseDir.toString(),
                ssTables.size(),
                levelNo,
                valueFileGroup,
                options
        );

        immutable.all().forEach(entry -> {
            DbKey dbKey = entry.key();
            ssTable.append(dbKey.key(), dbKey.column(), entry.value());
        });

        ssTables.add(ssTable);
    }

    public void merge(Level level) {
        if(isFull()) {
            mergeToNextLevel();
        }

        SsTable ssTable = new SsTable(
                baseDir.toString(),
                ssTables.size(),
                levelNo,
                valueFileGroup,
                options
        );

        level.all().forEach(dbIndex ->
                ssTable.append(dbIndex.key(), dbIndex.valueGlobalIndex())
        );

        ssTables.add(ssTable);
    }

    public List<DbIndex> all() {
        List<DbIndex> dbIndexList = new ArrayList<>();

        ssTables.forEach(ssTable -> dbIndexList.addAll(ssTable.dbIndexList()));
        dbIndexList.sort(Comparator.comparing(DbIndex::key));

        return dbIndexList;
    }

    public boolean isFull() {
        return ssTables.size() >= LEVEL_SSTABLE_COUNT;
    }

    public byte[] find(DbKey dbKey) {
        for(SsTable ssTable : ssTables) {
            if(ssTable.contains(dbKey)) {
                byte[] value = ssTable.find(dbKey);
                if(value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    public List<DbValue> find(byte[] key) {
        List<DbValue> values = new ArrayList<>();

        for(SsTable ssTable : ssTables) {
            values.addAll(ssTable.find(key));
        }

        return values;
    }

    public boolean contains(DbKey dbKey) {
        for(SsTable ssTable : ssTables) {
            if(ssTable.contains(dbKey)) {
                return true;
            }
        }

        return false;
    }

    public boolean delete(DbKey dbKey) {
        for(SsTable ssTable : ssTables) {
            if(ssTable.contains(dbKey) && ssTable.delete(dbKey)) {
                return true;
            }
        }
        return false;
    }

    private void mergeToNextLevel() {
        int nextLevelNo = levelNo + 1;
        Level nextLevel = parent.get(nextLevelNo);

        if(nextLevel == null) {
            nextLevel = new Level(parentDir, nextLevelNo, parent, valueFileGroup, options);
            parent.put(nextLevelNo, nextLevel);
        }

        nextLevel.merge(this);

        FileUtils.deleteSubs(baseDir);
        ssTables = new ArrayList<>(LEVEL_SSTABLE_COUNT);
    }
}
