package cn.iantech.mysql.itest;

public interface PlainNoteMapper {

    int insert(PlainNotePo po);

    int countByBody(String body);
}
