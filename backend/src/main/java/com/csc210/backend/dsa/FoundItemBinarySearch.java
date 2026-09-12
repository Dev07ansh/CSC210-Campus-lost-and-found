package com.csc210.backend.dsa;

import com.csc210.backend.model.FoundItem;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class FoundItemBinarySearch {
    public int lowerBound(List<FoundItem> items,LocalDateTime start){int left=0,right=items.size();while(left<right){int middle=left+(right-left)/2;if(items.get(middle).getDateTime().isBefore(start))left=middle+1;else right=middle;}return left;}
    public int upperBound(List<FoundItem> items,LocalDateTime end){int left=0,right=items.size();while(left<right){int middle=left+(right-left)/2;if(items.get(middle).getDateTime().isAfter(end))right=middle;else left=middle+1;}return left;}
    public List<FoundItem> searchByDateRange(List<FoundItem> items,LocalDateTime start,LocalDateTime end){if(items==null||items.isEmpty()||start==null||end==null||start.isAfter(end))return Collections.emptyList();return items.subList(lowerBound(items,start),upperBound(items,end));}
}
