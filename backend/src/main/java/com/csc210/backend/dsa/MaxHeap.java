package com.csc210.backend.dsa;

import java.util.*;

public class MaxHeap<T extends Comparable<? super T>> {
    private final List<T> elements=new ArrayList<>();
    public void offer(T value){if(value==null)throw new IllegalArgumentException("Heap values cannot be null");elements.add(value);up(elements.size()-1);}
    public T poll(){if(elements.isEmpty())throw new NoSuchElementException();T max=elements.get(0),last=elements.remove(elements.size()-1);if(!elements.isEmpty()){elements.set(0,last);down(0);}return max;}
    public boolean isEmpty(){return elements.isEmpty();} public int size(){return elements.size();}
    private void up(int child){while(child>0){int parent=(child-1)/2;if(elements.get(child).compareTo(elements.get(parent))<=0)return;swap(child,parent);child=parent;}}
    private void down(int parent){while(true){int left=parent*2+1,right=parent*2+2,largest=parent;if(left<elements.size()&&elements.get(left).compareTo(elements.get(largest))>0)largest=left;if(right<elements.size()&&elements.get(right).compareTo(elements.get(largest))>0)largest=right;if(largest==parent)return;swap(parent,largest);parent=largest;}}
    private void swap(int a,int b){T temp=elements.get(a);elements.set(a,elements.get(b));elements.set(b,temp);}
}
