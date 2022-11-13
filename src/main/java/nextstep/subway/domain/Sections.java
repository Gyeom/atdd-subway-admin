package nextstep.subway.domain;

import javax.persistence.CascadeType;
import javax.persistence.OneToMany;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Sections {

    private static final int COUNT_TO_CREATE_SECTION_BETWEEN_STATIONS = 3;
    private static final int NONE_SECTION = 0;
    private static final int LAST_SECTION = 1;
    private static final int COUNT_OF_ENDPOINT_SECTION = 1;
    private static final int COUNT_OF_MIDPOINT_SECTION = 2;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Section> sections = new ArrayList<>();

    protected Sections() {
    }

    public Sections(final Section... sections) {
        this.sections.addAll(asList(sections));
    }

    public void add(final Section newSection) {
        Section section = validateIfConnectableSectionWith(newSection);
        section.reArrangeWith(newSection);
        this.sections.add(newSection);
    }

    private Section validateIfConnectableSectionWith(final Section newSection) {
        Section section = findConnectableSectionWith(newSection);
        if (isIncludedBetweenStations(newSection, section)) {
            section.validateDistance(newSection);
        }
        return section;
    }

    private boolean isIncludedBetweenStations(final Section newSection, final Section section) {
        return section.isSameUpStation(newSection) || section.isSameDownStation(newSection);
    }

    private Section findConnectableSectionWith(final Section newSection) {
        return sections.stream()
                .filter(section -> isInsertable(section, newSection))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("새 구간을 연결할 수 있는 구간이 없습니다."));
    }

    private boolean isInsertable(final Section section, final Section newSection) {
        Set<Station> copy = new HashSet<>(section.getStations());
        copy.addAll(newSection.getStations());
        return copy.size() == COUNT_TO_CREATE_SECTION_BETWEEN_STATIONS
                && !Objects.equals(newSection.getDownStation(), newSection.getUpStation());
    }

    public List<Section> getSections() {
        return sections;
    }

    public void deleteByStationId(final Long stationId) {
        List<Section> sections = findSectionsByStationId(stationId);
        validateDeletableSection(sections);
        delete(sections);
    }

    private void delete(final List<Section> sections) {
        validateWrongSections(sections);
        if (sections.size() == COUNT_OF_ENDPOINT_SECTION) {
            removeEndPoint(sections);
            return;
        }
        connect(sections);
    }

    private void validateWrongSections(final List<Section> sections) {
        if (sections.size() > COUNT_OF_MIDPOINT_SECTION) {
            throw new IllegalArgumentException("구간의 수가 올바르지 않습니다.");
        }
    }

    private void validateDeletableSection(final List<Section> connectedSection) {
        if (connectedSection.size() == NONE_SECTION || sections.size() == LAST_SECTION) {
            throw new IllegalArgumentException("더 이상 삭제할 수 있는 구간이 없습니다.");
        }
    }

    private void removeEndPoint(final List<Section> sections) {
        Section section = sections.get(0);
        if (section.isAscentEndpoint()) {
            section.getDownStation().changeAscentEndPoint(true);
        } else if (section.isDeAscentEndpoint()) {
            section.getUpStation().changeAscentEndPoint(false);
        }
        removeSection(section);
    }

    private void connect(final List<Section> sections) {
        Section section1 = sections.get(0);
        Section section2 = sections.get(1);
        if (section1.getUpStation().getId().equals(section2.getDownStation().getId())) {
            connect(section1, section2);
        } else if (section2.getUpStation().getId().equals(section1.getDownStation().getId())) {
            connect(section2, section1);
        }
    }

    private void connect(final Section A, final Section B) {
        int distance = connectDistance(A, B);
        if (A.getDownStation().isDeAscentEndPoint()) {
            B.setDownStation(A.getDownStation());
            B.changeDistance(distance);
            removeSection(A);
        } else {
            A.setUpStation(B.getUpStation());
            A.changeDistance(distance);
            removeSection(B);
        }
    }

    private int connectDistance(final Section A, final Section B) {
        return A.getDistance() + B.getDistance();
    }

    private void removeSection(final Section section) {
        section.setLine(null);
        sections.remove(section);
    }

    private List<Section> findSectionsByStationId(final Long stationId) {
        return getSections().stream()
                .filter(section -> section.isInclude(stationId))
                .collect(Collectors.toList());
    }
}
